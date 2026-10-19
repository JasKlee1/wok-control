package com.wokcontrol.facturacion.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.facturacion.client.ParametrosClient;
import com.wokcontrol.facturacion.client.PedidosClient;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CobroRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CuentaMesa;
import com.wokcontrol.facturacion.dto.FacturacionDtos.DescuentoRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.LineaCuenta;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PedidoCuenta;
import com.wokcontrol.facturacion.entity.AutorizacionDescuento;
import com.wokcontrol.facturacion.entity.BloqueoPedido;
import com.wokcontrol.facturacion.entity.Factura;
import com.wokcontrol.facturacion.repository.BloqueoPedidoRepository;
import com.wokcontrol.facturacion.repository.FacturaRepository;

/**
 * Cobro de cuentas y emisión de comprobantes internos de venta.
 * <ul>
 * <li>Los valores se recalculan siempre en el servidor con los pedidos de pedidos-service; el
 * cliente solo envía qué pedidos cobra, los pagos y, si aplica, el descuento.</li>
 * <li>Doble cobro: la clave de idempotencia devuelve el mismo comprobante si la solicitud se repite y
 * el bloqueo por pedido (clave única) impide cobrar un pedido en dos comprobantes.</li>
 * <li>Descuentos: directos solo con permiso DESCUENTOS_AUTORIZAR; el cajero necesita un código de
 * autorización de un solo uso.</li>
 * <li>Tras emitir, se notifica a pedidos-service (idempotente). Si falla, el comprobante queda con
 * sincronización PENDIENTE y una tarea programada reintenta cada minuto.</li>
 * </ul>
 */
@Service
public class FacturacionService {

    private static final Logger log = LoggerFactory.getLogger(FacturacionService.class);

    private final FacturaRepository repo;
    private final BloqueoPedidoRepository bloqueos;
    private final PedidosClient pedidos;
    private final ParametrosClient parametros;
    private final DescuentoService descuentos;
    private final SecuenciaService secuencias;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;
    private final ZoneId zona;

    public FacturacionService(FacturaRepository repo, BloqueoPedidoRepository bloqueos, PedidosClient pedidos,
            ParametrosClient parametros, DescuentoService descuentos, SecuenciaService secuencias, MongoTemplate mongo,
            AuditoriaPublisher auditoria, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.repo = repo;
        this.bloqueos = bloqueos;
        this.pedidos = pedidos;
        this.parametros = parametros;
        this.descuentos = descuentos;
        this.secuencias = secuencias;
        this.mongo = mongo;
        this.auditoria = auditoria;
        this.zona = ZoneId.of(zona);
    }

    public Factura obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Comprobante", id));
    }

    public List<Factura> buscar(LocalDate desde, LocalDate hasta, Factura.Estado estado, String cajero, String mesaId) {
        Query q = new Query();
        if (desde != null || hasta != null) {
            Criteria c = Criteria.where("emitidaEn");
            if (desde != null) {
                c = c.gte(desde.atStartOfDay(zona).toInstant());
            }
            if (hasta != null) {
                c = c.lt(hasta.plusDays(1).atStartOfDay(zona).toInstant());
            }
            q.addCriteria(c);
        }
        if (estado != null) {
            q.addCriteria(Criteria.where("estado").is(estado));
        }
        if (cajero != null && !cajero.isBlank()) {
            q.addCriteria(Criteria.where("cajero").is(cajero));
        }
        if (mesaId != null && !mesaId.isBlank()) {
            q.addCriteria(Criteria.where("mesaId").is(mesaId));
        }
        q.with(Sort.by(Sort.Direction.DESC, "emitidaEn")).limit(500);
        return mongo.find(q, Factura.class);
    }

    /** Cuenta de la mesa: pedidos entregados sin pagar, con totales calculados aquí. */
    public CuentaMesa cuenta(String mesaId) {
        PedidosClient.Mesa mesa = pedidos.mesa(mesaId);
        List<PedidosClient.Pedido> facturables = pedidos.facturables(mesaId);
        List<LineaCuenta> lineas = lineas(facturables);
        BigDecimal subtotal = lineas.stream().map(LineaCuenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal impuestoPct = parametros.obtener().impuestoConsumoPorcentaje();
        CalculadoraCuenta.Totales t = subtotal.signum() > 0 ? CalculadoraCuenta.calcular(subtotal, BigDecimal.ZERO, impuestoPct)
                : new CalculadoraCuenta.Totales(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        int enCurso = Math.max(0, mesa.pedidosActivos() - facturables.size());
        return new CuentaMesa(mesa.id(), mesa.numero(),
                facturables.stream().map(p -> new PedidoCuenta(p.id(), p.numero(), p.total(), p.entregadoEn())).toList(),
                lineas, t.subtotal(), impuestoPct, t.impuesto(), t.total(), enCurso);
    }

    public Factura cobrar(CobroRequest req) {
        Factura previa = repo.findByClaveIdempotencia(req.claveIdempotencia()).orElse(null);
        if (previa != null) {
            log.info("Cobro repetido con clave {}: se devuelve el comprobante {}", req.claveIdempotencia(), previa.getNumero());
            return previa;
        }
        UsuarioActual cajero = UsuarioActual.requerido();
        PedidosClient.Mesa mesa = pedidos.mesa(req.mesaId());
        Map<String, PedidosClient.Pedido> facturables = pedidos.facturables(req.mesaId()).stream()
                .collect(Collectors.toMap(PedidosClient.Pedido::id, Function.identity()));
        Set<String> solicitados = req.pedidoIds() == null || req.pedidoIds().isEmpty()
                ? new LinkedHashSet<>(facturables.keySet()) : new LinkedHashSet<>(req.pedidoIds());
        if (solicitados.isEmpty()) {
            throw new ReglaNegocioException("La mesa " + mesa.numero() + " no tiene pedidos entregados pendientes de pago");
        }
        List<PedidosClient.Pedido> aCobrar = new ArrayList<>();
        for (String id : solicitados) {
            PedidosClient.Pedido p = facturables.get(id);
            if (p == null) {
                throw new ReglaNegocioException("El pedido " + id + " no está entregado o ya fue pagado; actualice la cuenta");
            }
            aCobrar.add(p);
        }
        List<LineaCuenta> lineas = lineas(aCobrar);
        BigDecimal subtotal = lineas.stream().map(LineaCuenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        ParametrosClient.Parametros params = parametros.obtener();

        // descuento: autorización previa obligatoria salvo que el usuario tenga el permiso
        DescuentoRequest d = req.descuento();
        AutorizacionDescuento autorizacion = null;
        BigDecimal porcentaje = BigDecimal.ZERO;
        String autorizadoPor = null;
        if (d != null && d.porcentaje() != null && d.porcentaje().signum() > 0) {
            porcentaje = d.porcentaje();
            if (cajero.tiene(Permisos.DESCUENTOS_AUTORIZAR)) {
                if (d.motivo() == null || d.motivo().trim().length() < 5) {
                    throw new ReglaNegocioException("Indique el motivo del descuento");
                }
                autorizadoPor = cajero.responsable();
            } else if (d.codigoAutorizacion() == null || d.codigoAutorizacion().isBlank()) {
                throw new OperacionNoPermitidaException("El descuento requiere un código de autorización del administrador");
            } else {
                autorizacion = descuentos.consumir(d.codigoAutorizacion(), porcentaje, req.mesaId());
                autorizadoPor = autorizacion.getAutorizadoPor();
            }
        }

        try {
            CalculadoraCuenta.Totales t = CalculadoraCuenta.calcular(subtotal, porcentaje, params.impuestoConsumoPorcentaje());
            CalculadoraCuenta.ResultadoPagos rp = CalculadoraCuenta.validarPagos(req.pagos(), t.total());

            Factura f = new Factura();
            f.setNumero(secuencias.siguienteFormateado("comprobantes", "TIQ-"));
            f.setClaveIdempotencia(req.claveIdempotencia());
            f.setMesaId(mesa.id());
            f.setMesaNumero(mesa.numero());
            f.setPedidoIds(new ArrayList<>(solicitados));
            f.setLineas(lineas.stream().map(l -> new Factura.Linea(l.pedidoId(), l.pedidoNumero(), l.productoId(),
                    l.descripcion(), l.cantidad(), l.precioUnitario(), l.subtotal())).toList());
            f.setSubtotal(t.subtotal());
            if (t.descuento().signum() > 0) {
                f.setDescuento(new Factura.Descuento(porcentaje, t.descuento(),
                        autorizacion != null ? autorizacion.getMotivo() : d.motivo().trim(), autorizadoPor,
                        autorizacion != null ? autorizacion.getCodigo() : null));
            }
            f.setBaseGravable(t.base());
            f.setImpuestoPorcentaje(params.impuestoConsumoPorcentaje());
            f.setImpuestoValor(t.impuesto());
            f.setTotal(t.total());
            f.setPagos(req.pagos().stream().map(p -> new Factura.Pago(p.metodo(), CalculadoraCuenta.pesos(p.monto()),
                    p.referencia() == null || p.referencia().isBlank() ? null : p.referencia().trim(), "APROBADO")).toList());
            f.setTotalRecibido(rp.recibido());
            f.setCambio(rp.cambio());
            f.setEstado(Factura.Estado.EMITIDA);
            f.setCajero(cajero.responsable());
            f.setCajeroNombre(cajero.nombre());
            f.setEmitidaEn(Instant.now());
            f.setEmisor(new Factura.DatosEmisor(params.nombreRestaurante(), params.nit(), params.direccion(),
                    params.telefono(), params.mensajeComprobante()));
            f.setSincronizacion(Factura.Sincronizacion.PENDIENTE);

            bloquearPedidos(f);
            Factura g;
            try {
                g = repo.save(f);
            } catch (DuplicateKeyException ex) {
                liberarPedidos(f.getPedidoIds());
                Factura repetida = repo.findByClaveIdempotencia(req.claveIdempotencia()).orElse(null);
                if (repetida != null) {
                    return repetida;
                }
                throw new ConflictoException("No se pudo emitir el comprobante; intente de nuevo");
            }
            if (autorizacion != null) {
                descuentos.vincularFactura(autorizacion.getId(), g.getId());
            }
            auditoria.registrar("COBRAR", "Comprobante", g.getNumero(), "Mesa " + g.getMesaNumero() + " · $" + g.getTotal()
                    + (g.getDescuento() != null ? " · descuento " + g.getDescuento().porcentaje() + "%" : ""));
            sincronizarPagos(g);
            return g;
        } catch (RuntimeException ex) {
            if (autorizacion != null) {
                descuentos.liberar(autorizacion.getId());
            }
            throw ex;
        }
    }

    public Factura anular(String id, String motivo) {
        Factura f = obtener(id);
        if (f.getEstado() == Factura.Estado.ANULADA) {
            throw new ConflictoException("El comprobante ya está anulado");
        }
        if (f.getCierreId() != null) {
            throw new ReglaNegocioException("El comprobante ya está incluido en un cierre de caja y no puede anularse");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new ReglaNegocioException("Indique el motivo de la anulación (mínimo 5 caracteres)");
        }
        f.setEstado(Factura.Estado.ANULADA);
        f.setAnulacion(new Factura.Anulacion(Instant.now(), UsuarioActual.responsableActual(), motivo.trim()));
        f.setSincronizacion(Factura.Sincronizacion.PENDIENTE_ANULACION);
        f.setIntentosSincronizacion(0);
        Factura g = repo.save(f);
        liberarPedidos(g.getPedidoIds());
        auditoria.registrar("ANULAR", "Comprobante", g.getNumero(), motivo.trim());
        sincronizarPagos(g);
        return g;
    }

    /**
     * Notifica a pedidos-service el pago (o su reversión por anulación). Ambas operaciones son
     * idempotentes en pedidos-service, por eso se pueden reintentar sin riesgo.
     */
    public Factura sincronizarPagos(Factura f) {
        if (f.getSincronizacion() == Factura.Sincronizacion.COMPLETA) {
            return f;
        }
        boolean anulacion = f.getSincronizacion() == Factura.Sincronizacion.PENDIENTE_ANULACION;
        try {
            for (String pedidoId : f.getPedidoIds()) {
                if (anulacion) {
                    pedidos.revertirPago(pedidoId, f.getId());
                } else {
                    pedidos.marcarPagado(pedidoId, f.getId());
                }
            }
            f.setSincronizacion(Factura.Sincronizacion.COMPLETA);
            f.setErrorSincronizacion(null);
        } catch (WokException ex) {
            f.setIntentosSincronizacion(f.getIntentosSincronizacion() + 1);
            f.setErrorSincronizacion(ex.getMessage());
            log.warn("Comprobante {}: sincronización con pedidos pendiente (intento {}): {}", f.getNumero(),
                    f.getIntentosSincronizacion(), ex.getMessage());
        }
        return repo.save(f);
    }

    public Factura reintentar(String id) {
        return sincronizarPagos(obtener(id));
    }

    public List<Factura> pendientesSincronizar() {
        return repo.findBySincronizacionIn(List.of(Factura.Sincronizacion.PENDIENTE, Factura.Sincronizacion.PENDIENTE_ANULACION));
    }

    private void bloquearPedidos(Factura f) {
        List<String> bloqueados = new ArrayList<>();
        try {
            for (String pedidoId : f.getPedidoIds()) {
                mongo.insert(new BloqueoPedido(pedidoId, f.getNumero(), Instant.now()));
                bloqueados.add(pedidoId);
            }
        } catch (DuplicateKeyException ex) {
            liberarPedidos(bloqueados);
            throw new ConflictoException("Uno de los pedidos ya fue cobrado en otro comprobante; actualice la cuenta");
        }
    }

    private void liberarPedidos(List<String> pedidoIds) {
        if (!pedidoIds.isEmpty()) {
            bloqueos.deleteAllById(pedidoIds);
        }
    }

    private static List<LineaCuenta> lineas(List<PedidosClient.Pedido> lista) {
        List<LineaCuenta> r = new ArrayList<>();
        for (PedidosClient.Pedido p : lista) {
            for (PedidosClient.Item i : p.items()) {
                BigDecimal sub = i.precioUnitario().multiply(BigDecimal.valueOf(i.cantidad()));
                r.add(new LineaCuenta(p.id(), p.numero(), i.productoId(), i.nombre(), i.cantidad(), i.precioUnitario(), sub));
            }
        }
        return r;
    }
}
