package com.wokcontrol.proveedores.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.proveedores.client.InventarioClient;
import com.wokcontrol.proveedores.client.InventarioClient.InsumoResumen;
import com.wokcontrol.proveedores.dto.ProveedorDtos;
import com.wokcontrol.proveedores.dto.ProveedorDtos.EntregaRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.LineaRecepcionRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.OrdenRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ResumenCompras;
import com.wokcontrol.proveedores.entity.OrdenCompra;
import com.wokcontrol.proveedores.entity.OrdenCompra.Estado;
import com.wokcontrol.proveedores.entity.Proveedor;
import com.wokcontrol.proveedores.repository.OrdenCompraRepository;
import com.wokcontrol.proveedores.repository.ProveedorRepository;

/**
 * Órdenes de compra: BORRADOR → ENVIADA → CONFIRMADA → ENTREGA_REPORTADA → RECIBIDA (o CANCELADA).
 * <ul>
 * <li>Solo en BORRADOR se modifican los ítems.</li>
 * <li>El proveedor confirma y reporta la entrega desde su portal, únicamente en sus propias órdenes.</li>
 * <li>La recepción la confirma inventario/compras con las cantidades realmente recibidas. Primero se
 * guarda la recepción en la orden y luego se envía a inventario-service con la referencia OC-id
 * (idempotente). Si inventario no responde, la orden queda con la recepción pendiente de
 * sincronizar y se puede reintentar sin duplicar existencias.</li>
 * </ul>
 */
@Service
public class OrdenCompraService {

    private static final Logger log = LoggerFactory.getLogger(OrdenCompraService.class);

    private final OrdenCompraRepository repo;
    private final ProveedorRepository proveedores;
    private final InventarioClient inventario;
    private final SecuenciaService secuencias;
    private final AuditoriaPublisher auditoria;
    private final ZoneId zona;

    public OrdenCompraService(OrdenCompraRepository repo, ProveedorRepository proveedores, InventarioClient inventario,
            SecuenciaService secuencias, AuditoriaPublisher auditoria, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.repo = repo;
        this.proveedores = proveedores;
        this.inventario = inventario;
        this.secuencias = secuencias;
        this.auditoria = auditoria;
        this.zona = ZoneId.of(zona);
    }

    public List<OrdenCompra> listar(Estado estado, String proveedorId) {
        List<OrdenCompra> todas = proveedorId != null && !proveedorId.isBlank()
                ? repo.findByProveedorIdOrderByCreadoEnDesc(proveedorId)
                : repo.findAllByOrderByCreadoEnDesc();
        return estado == null ? todas : todas.stream().filter(o -> o.getEstado() == estado).toList();
    }

    public OrdenCompra obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Orden de compra", id));
    }

    /** Órdenes del proveedor autenticado (sin borradores, que aún no le han sido enviados). */
    public List<OrdenCompra> mias() {
        return repo.findByProveedorIdAndEstadoNotOrderByCreadoEnDesc(proveedorActual(), Estado.BORRADOR);
    }

    public OrdenCompra miOrden(String id) {
        OrdenCompra o = obtener(id);
        if (!o.getProveedorId().equals(proveedorActual()) || o.getEstado() == Estado.BORRADOR) {
            // no revela la existencia de órdenes de otros proveedores
            throw new RecursoNoEncontradoException("Orden de compra", id);
        }
        return o;
    }

    public OrdenCompra crear(OrdenRequest req) {
        Proveedor prov = proveedorActivo(req.proveedorId());
        OrdenCompra o = new OrdenCompra();
        o.setNumero(secuencias.siguienteFormateado("ordenes_compra", "OC-"));
        o.setProveedorId(prov.getId());
        o.setProveedorNombre(prov.getNombreComercial());
        o.setItems(construirItems(req.items()));
        o.setFechaEntregaEsperada(validarFecha(req.fechaEntregaEsperada()));
        o.setNotas(vacio(req.notas()));
        o.setCreadoPor(UsuarioActual.responsableActual());
        o.registrar(Estado.BORRADOR.name(), o.getCreadoPor(), "Orden creada");
        OrdenCompra g = repo.save(o);
        auditoria.registrar("CREAR", "OrdenCompra", g.getNumero(), prov.getNombreComercial() + " $" + g.getTotal());
        return g;
    }

    public OrdenCompra actualizar(String id, OrdenRequest req) {
        OrdenCompra o = obtener(id);
        if (o.getEstado() != Estado.BORRADOR) {
            throw new ReglaNegocioException("Solo se modifican órdenes en borrador");
        }
        Proveedor prov = proveedorActivo(req.proveedorId());
        o.setProveedorId(prov.getId());
        o.setProveedorNombre(prov.getNombreComercial());
        o.setItems(construirItems(req.items()));
        o.setFechaEntregaEsperada(validarFecha(req.fechaEntregaEsperada()));
        o.setNotas(vacio(req.notas()));
        o.registrar("MODIFICADA", UsuarioActual.responsableActual(), null);
        return repo.save(o);
    }

    public OrdenCompra enviar(String id) {
        OrdenCompra o = obtener(id);
        exigir(o, Estado.BORRADOR, "enviar");
        proveedorActivo(o.getProveedorId());
        return cambiar(o, Estado.ENVIADA, "Enviada al proveedor");
    }

    /** El proveedor (desde su portal) o compras en su nombre confirman la orden. */
    public OrdenCompra confirmar(String id) {
        OrdenCompra o = ordenAccesible(id);
        exigir(o, Estado.ENVIADA, "confirmar");
        return cambiar(o, Estado.CONFIRMADA, "Confirmada por " + UsuarioActual.responsableActual());
    }

    /** Información de entrega reportada por el proveedor. No modifica inventario. */
    public OrdenCompra reportarEntrega(String id, EntregaRequest req) {
        OrdenCompra o = ordenAccesible(id);
        exigir(o, Estado.CONFIRMADA, "reportar la entrega de");
        o.setEntrega(new OrdenCompra.Entrega(Instant.now(), req.fechaEntrega(), vacio(req.documento()),
                vacio(req.observaciones()), UsuarioActual.responsableActual()));
        return cambiar(o, Estado.ENTREGA_REPORTADA, "Entrega " + req.fechaEntrega() + (req.documento() != null ? " doc " + req.documento() : ""));
    }

    public OrdenCompra recibir(String id, List<LineaRecepcionRequest> lineas, String observaciones) {
        OrdenCompra o = obtener(id);
        if (o.getRecepcion() != null && !o.getRecepcion().sincronizadaInventario()) {
            throw new ReglaNegocioException("La orden ya tiene una recepción pendiente de sincronizar; use Reintentar");
        }
        if (!o.getEstado().permiteRecepcion()) {
            throw new ReglaNegocioException("No se puede recibir una orden en estado " + o.getEstado().etiqueta());
        }
        Map<String, LineaRecepcionRequest> porInsumo = new LinkedHashMap<>();
        for (LineaRecepcionRequest l : lineas) {
            porInsumo.put(l.insumoId(), l);
        }
        List<OrdenCompra.Item> items = new ArrayList<>();
        boolean algo = false;
        for (OrdenCompra.Item it : o.getItems()) {
            LineaRecepcionRequest l = porInsumo.get(it.insumoId());
            BigDecimal recibida = l == null || l.cantidadRecibida() == null ? BigDecimal.ZERO : l.cantidadRecibida();
            if (recibida.signum() < 0) {
                throw new ReglaNegocioException("La cantidad recibida de " + it.nombreInsumo() + " no puede ser negativa");
            }
            if (recibida.compareTo(it.cantidad()) > 0) {
                throw new ReglaNegocioException("Se recibió más " + it.nombreInsumo() + " de lo pedido ("
                        + it.cantidad().stripTrailingZeros().toPlainString() + "); registre la diferencia como una entrada aparte");
            }
            algo |= recibida.signum() > 0;
            items.add(new OrdenCompra.Item(it.insumoId(), it.nombreInsumo(), it.unidad(), it.cantidad(), it.costoUnitario(),
                    recibida, l == null ? null : vacio(l.lote()), l == null ? null : l.fechaVencimiento()));
        }
        if (!algo) {
            throw new ReglaNegocioException("Registre al menos una cantidad recibida mayor que cero");
        }
        o.setItems(items);
        o.setRecepcion(new OrdenCompra.Recepcion(Instant.now(), UsuarioActual.responsableActual(), vacio(observaciones), false, null));
        o.registrar("RECEPCION_REGISTRADA", UsuarioActual.responsableActual(), null);
        OrdenCompra guardada = repo.save(o);
        return sincronizarInventario(guardada);
    }

    /** Envía (o reenvía) la recepción guardada a inventario-service. Idempotente por OC-id. */
    public OrdenCompra sincronizarInventario(OrdenCompra o) {
        if (o.getRecepcion() == null) {
            throw new ReglaNegocioException("La orden no tiene una recepción registrada");
        }
        if (o.getRecepcion().sincronizadaInventario()) {
            return o;
        }
        List<InventarioClient.LineaRecepcion> lineas = o.getItems().stream()
                .filter(i -> i.cantidadRecibida() != null && i.cantidadRecibida().signum() > 0)
                .map(i -> new InventarioClient.LineaRecepcion(i.insumoId(), i.cantidadRecibida(), i.costoUnitario(), i.lote(),
                        i.fechaVencimiento()))
                .toList();
        OrdenCompra.Recepcion r = o.getRecepcion();
        try {
            inventario.registrarRecepcion(new InventarioClient.RecepcionRequest(o.referenciaInventario(), o.getProveedorId(), lineas));
        } catch (WokException ex) {
            log.warn("Recepción de {} pendiente de sincronizar: {}", o.getNumero(), ex.getMessage());
            o.setRecepcion(new OrdenCompra.Recepcion(r.fecha(), r.recibidoPor(), r.observaciones(), false, ex.getMessage()));
            repo.save(o);
            throw ex;
        }
        o.setRecepcion(new OrdenCompra.Recepcion(r.fecha(), r.recibidoPor(), r.observaciones(), true, null));
        return cambiar(o, Estado.RECIBIDA, "Mercancía ingresada al inventario: " + lineas.size() + " insumos");
    }

    public OrdenCompra reintentarSincronizacion(String id) {
        return sincronizarInventario(obtener(id));
    }

    public OrdenCompra cancelar(String id, String motivo) {
        OrdenCompra o = obtener(id);
        if (!o.getEstado().permiteCancelar()) {
            throw new ReglaNegocioException("No se puede cancelar una orden " + o.getEstado().etiqueta().toLowerCase());
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new ReglaNegocioException("Indique el motivo de la cancelación (mínimo 5 caracteres)");
        }
        o.setMotivoCancelacion(motivo.trim());
        return cambiar(o, Estado.CANCELADA, motivo.trim());
    }

    public ResumenCompras resumen() {
        List<OrdenCompra> todas = repo.findAllByOrderByCreadoEnDesc();
        Instant inicioMes = LocalDate.now(zona).withDayOfMonth(1).atStartOfDay(zona).toInstant();
        BigDecimal compradoMes = todas.stream()
                .filter(o -> o.getEstado() == Estado.RECIBIDA && o.getRecepcion() != null && o.getRecepcion().fecha().isAfter(inicioMes))
                .map(OrdenCompra::getTotalRecibido).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenCompras(contar(todas, Estado.BORRADOR), contar(todas, Estado.ENVIADA), contar(todas, Estado.CONFIRMADA),
                contar(todas, Estado.ENTREGA_REPORTADA), todas.stream().filter(o -> Estado.PENDIENTES.contains(o.getEstado())).count(),
                todas.stream().filter(o -> o.getRecepcion() != null && !o.getRecepcion().sincronizadaInventario()).count(),
                compradoMes, proveedores.findAll().stream().filter(Proveedor::isActivo).count());
    }

    public ProveedorDtos.HistorialProveedor historial(String proveedorId) {
        List<OrdenCompra> ordenes = repo.findByProveedorIdOrderByCreadoEnDesc(proveedorId);
        List<OrdenCompra> recibidas = ordenes.stream().filter(o -> o.getEstado() == Estado.RECIBIDA).toList();
        return new ProveedorDtos.HistorialProveedor(ordenes.size(), recibidas.size(),
                recibidas.stream().map(OrdenCompra::getTotalRecibido).reduce(BigDecimal.ZERO, BigDecimal::add),
                ordenes.stream().limit(10).map(ProveedorDtos.OrdenResponse::de).toList());
    }

    // ------------------------------------------------------------------ apoyo

    private OrdenCompra ordenAccesible(String id) {
        UsuarioActual u = UsuarioActual.requerido();
        if (u.tiene(Permisos.COMPRAS_GESTIONAR)) {
            return obtener(id);
        }
        if (u.tiene(Permisos.PORTAL_PROVEEDOR)) {
            return miOrden(id);
        }
        throw new OperacionNoPermitidaException("No tiene acceso a esta orden de compra");
    }

    private static String proveedorActual() {
        String id = UsuarioActual.requerido().proveedorId();
        if (id == null) {
            throw new OperacionNoPermitidaException("Su usuario no está vinculado a ningún proveedor");
        }
        return id;
    }

    private Proveedor proveedorActivo(String id) {
        Proveedor p = proveedores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proveedor", id));
        if (!p.isActivo()) {
            throw new ReglaNegocioException("El proveedor " + p.getNombreComercial() + " está inactivo");
        }
        return p;
    }

    private List<OrdenCompra.Item> construirItems(List<ProveedorDtos.ItemOrdenRequest> solicitados) {
        if (solicitados == null || solicitados.isEmpty()) {
            throw new ReglaNegocioException("Agregue al menos un insumo");
        }
        Map<String, InsumoResumen> insumos = inventario.insumos().stream()
                .collect(Collectors.toMap(InsumoResumen::id, Function.identity()));
        Map<String, OrdenCompra.Item> items = new LinkedHashMap<>();
        for (ProveedorDtos.ItemOrdenRequest s : solicitados) {
            InsumoResumen i = insumos.get(s.insumoId());
            if (i == null) {
                throw new ReglaNegocioException("El insumo " + s.insumoId() + " no existe o está inactivo");
            }
            if (items.containsKey(i.id())) {
                throw new ReglaNegocioException("El insumo " + i.nombre() + " está repetido en la orden");
            }
            items.put(i.id(), new OrdenCompra.Item(i.id(), i.nombre(), i.unidad(), s.cantidad(), s.costoUnitario(), null, null, null));
        }
        return new ArrayList<>(items.values());
    }

    private LocalDate validarFecha(LocalDate f) {
        if (f != null && f.isBefore(LocalDate.now(zona))) {
            throw new ReglaNegocioException("La fecha de entrega esperada no puede estar en el pasado");
        }
        return f;
    }

    private static void exigir(OrdenCompra o, Estado esperado, String accion) {
        if (o.getEstado() != esperado) {
            throw new ReglaNegocioException("No se puede " + accion + " una orden en estado " + o.getEstado().etiqueta());
        }
    }

    private OrdenCompra cambiar(OrdenCompra o, Estado destino, String detalle) {
        o.setEstado(destino);
        o.registrar(destino.name(), UsuarioActual.responsableActual(), detalle);
        OrdenCompra g = repo.save(o);
        auditoria.registrar(destino.name(), "OrdenCompra", g.getNumero(), detalle);
        return g;
    }

    private static long contar(List<OrdenCompra> l, Estado e) {
        return l.stream().filter(o -> o.getEstado() == e).count();
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
