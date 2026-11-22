package com.wokcontrol.domicilios.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.domicilios.client.FacturacionClient;
import com.wokcontrol.domicilios.client.ParametrosClient;
import com.wokcontrol.domicilios.client.PedidosClient;
import com.wokcontrol.domicilios.client.QrRutaClient;
import com.wokcontrol.domicilios.client.UsuariosClient;
import com.wokcontrol.domicilios.dto.DomicilioDtos;
import com.wokcontrol.domicilios.dto.DomicilioDtos.CobroRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.Domiciliario;
import com.wokcontrol.domicilios.dto.DomicilioDtos.DomicilioRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.EdicionRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.MetodoCobro;
import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.entity.EstadoDomicilio;
import com.wokcontrol.domicilios.repository.DomicilioRepository;

/**
 * v2 · CR-001 (épica 9): ciclo de vida del domicilio. Reglas principales (spec 009, sección 6):
 * <ul>
 * <li>RN-11: teléfono de 10 dígitos y dirección obligatoria (AC-047.2).</li>
 * <li>RN-12: valor a cobrar = platos + impuesto al consumo sobre los platos + valor del domicilio.</li>
 * <li>RN-13: se edita o cancela solo antes del despacho (AC-047.3); los platos no se editan porque ya
 * están en cocina: se cancela y se registra de nuevo.</li>
 * <li>RN-14: solo se asigna un pedido LISTO (AC-048.2) a un domiciliario activo sin entregas en camino.</li>
 * <li>RN-15: el domiciliario solo ve y opera lo que tiene asignado (AC-051.1).</li>
 * <li>RN-16: ENTREGADO solo desde EN_CAMINO (AC-049.2) y con el cobro que cubra el total (AC-050.2).</li>
 * <li>RN-17: NO_ENTREGADO exige motivo de al menos 10 caracteres (AC-052.2) y anula sin factura.</li>
 * <li>RN-18: cada cambio de estado queda en el historial y en la auditoría central (AC-053.2). Ley 1581:
 * ni el teléfono ni la dirección se escriben en la auditoría ni en los registros.</li>
 * </ul>
 */
@Service
public class DomicilioService {

    private static final Logger log = LoggerFactory.getLogger(DomicilioService.class);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static final String TRANSICION_INVALIDA = "TRANSICION_INVALIDA";
    public static final String PEDIDO_NO_LISTO = "PEDIDO_NO_LISTO";
    public static final String PAGO_INSUFICIENTE = "PAGO_INSUFICIENTE";
    public static final String MOTIVO_OBLIGATORIO = "MOTIVO_OBLIGATORIO";
    public static final String YA_DESPACHADO = "YA_DESPACHADO";
    public static final String DOMICILIARIO_NO_DISPONIBLE = "DOMICILIARIO_NO_DISPONIBLE";
    public static final int MOTIVO_MINIMO = 10;

    private final DomicilioRepository repo;
    private final PedidosClient pedidos;
    private final UsuariosClient usuarios;
    private final FacturacionClient facturacion;
    private final ParametrosClient parametros;
    private final QrRutaClient qr;
    private final SecuenciaService secuencias;
    private final AuditoriaPublisher auditoria;

    public DomicilioService(DomicilioRepository repo, PedidosClient pedidos, UsuariosClient usuarios,
            FacturacionClient facturacion, ParametrosClient parametros, QrRutaClient qr, SecuenciaService secuencias,
            AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
        this.facturacion = facturacion;
        this.parametros = parametros;
        this.qr = qr;
        this.secuencias = secuencias;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------ consultas

    public Domicilio obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Domicilio", id));
    }

    /**
     * AC-051.1 (RN-15): quien gestiona domicilios ve cualquiera; el domiciliario solo los suyos. Si pide
     * uno ajeno recibe 403 (no 404, para que quede claro que existe pero no le corresponde).
     */
    public Domicilio obtenerPara(String id, UsuarioActual u) {
        Domicilio d = obtener(id);
        if (!u.tiene(Permisos.DOMICILIOS_GESTIONAR) && !d.esDe(u.responsable())) {
            throw new OperacionNoPermitidaException("El domicilio " + d.getNumero() + " no está asignado a usted");
        }
        return d;
    }

    /** Domicilios en curso del más antiguo al más reciente, con el avance de cocina al día. */
    public List<Domicilio> activos() {
        List<Domicilio> l = repo.findByEstadoInOrderByCreadoEnAsc(EstadoDomicilio.ACTIVOS);
        sincronizarCocina(l);
        return l.stream().filter(d -> EstadoDomicilio.ACTIVOS.contains(d.getEstado())).toList();
    }

    public List<Domicilio> cerradosRecientes() {
        return repo.findTop30ByEstadoInOrderByCerradoEnDesc(EstadoDomicilio.FINALES);
    }

    /** AC-049.1: solo los asignados al domiciliario (asignados y en camino). */
    public List<Domicilio> mios(String domiciliario) {
        return repo.findByDomiciliarioAndEstadoInOrderByAsignadoEnAsc(domiciliario, EstadoDomicilio.DEL_DOMICILIARIO);
    }

    /** HU-048: domiciliarios activos; DISPONIBLE si no tiene entregas en camino (está en el restaurante). */
    public List<Domiciliario> domiciliarios() {
        List<Domiciliario> r = new ArrayList<>();
        for (UsuariosClient.Persona p : usuarios.domiciliarios()) {
            long enCamino = repo.countByDomiciliarioAndEstado(p.username(), EstadoDomicilio.EN_CAMINO);
            long asignados = repo.countByDomiciliarioAndEstado(p.username(), EstadoDomicilio.ASIGNADO);
            r.add(new Domiciliario(p.username(), p.nombre(), enCamino == 0, enCamino, asignados));
        }
        return r;
    }

    public List<Domicilio.CambioEstado> historial(String id, UsuarioActual u) {
        return obtenerPara(id, u).getHistorial();
    }

    /** API externa (ADR-005): PNG del QR con la ruta hasta la dirección del cliente. */
    public Optional<byte[]> qrRuta(String id, UsuarioActual u) {
        Domicilio d = obtenerPara(id, u);
        return qr.qrRuta(d.getNumero(), d.getCliente().direccion());
    }

    public String urlRuta(Domicilio d) {
        return qr.urlRuta(d.getCliente().direccion());
    }

    // ------------------------------------------------------------------ HU-047 · registrar, editar, cancelar

    /**
     * AC-047.1: registra el domicilio y su pedido va a cocina. El pedido se crea primero en pedidos-service
     * (que cotiza los platos en la carta vigente y descuenta inventario); si falla, no queda domicilio.
     */
    public Domicilio registrar(DomicilioRequest req) {
        Domicilio.Cliente cliente = validarCliente(req.cliente(), req.telefono(), req.direccion(), req.referencia());
        BigDecimal valorDomicilio = validarValor(req.valorDomicilio());
        if (req.items() == null || req.items().isEmpty()) {
            throw new CampoInvalidoException("items", "Agregue al menos un plato");
        }
        UsuarioActual u = UsuarioActual.requerido();
        String id = new ObjectId().toHexString();
        String numero = secuencias.siguienteFormateado("domicilios", "D-");
        String observaciones = limpiar(req.observaciones());

        PedidosClient.PedidoCocina pc = pedidos.crear(new PedidosClient.PedidoDomicilioRequest(id, numero,
                req.items().stream().map(i -> new PedidosClient.ItemSolicitado(i.tipo().name(), i.productoId(), i.cantidad(),
                        i.nivelPicante(), limpiar(i.notas()))).toList(),
                observaciones));

        Domicilio d = new Domicilio();
        d.setId(id);
        d.setNumero(numero);
        d.setCliente(cliente);
        d.setObservaciones(observaciones);
        d.setPedidoId(pc.id());
        d.setPedidoNumero(pc.numero());
        d.setLineas(pc.items() == null ? List.of() : pc.items().stream()
                .map(i -> new Domicilio.Linea(i.productoId(), i.nombre(), i.cantidad(), i.precioUnitario(), i.subtotal())).toList());
        d.setValorDomicilio(valorDomicilio);
        d.setImpuestoPorcentaje(parametros.impuestoConsumoPorcentaje());
        d.setSubtotalPlatos(pesos(pc.total()));
        recalcularTotal(d);
        d.setRegistradoPor(u.responsable());
        d.setRegistradoPorNombre(u.nombre());
        d.cambiarEstado(EstadoDomicilio.RECIBIDO, u.responsable(), "Registrado por teléfono; pedido " + pc.numero() + " enviado a cocina");
        Domicilio g;
        try {
            g = repo.save(d);
        } catch (RuntimeException ex) {
            // no se pudo guardar: el pedido no debe quedar en cocina sin domicilio
            anularPedidoSinFallar(pc.id(), "El domicilio " + numero + " no se pudo guardar");
            throw ex;
        }
        auditar(g, null, "Pedido " + pc.numero() + " · " + g.getUnidades() + " productos · total $" + g.getTotal().toPlainString());
        return g;
    }

    /** AC-047.3 (RN-13): solo antes del despacho. Los platos no se editan: ya están en cocina. */
    public Domicilio editar(String id, EdicionRequest req) {
        Domicilio d = obtener(id);
        exigirAntesDelDespacho(d, "editar");
        d.setCliente(validarCliente(req.cliente(), req.telefono(), req.direccion(), req.referencia()));
        d.setValorDomicilio(validarValor(req.valorDomicilio()));
        d.setObservaciones(limpiar(req.observaciones()));
        recalcularTotal(d);
        Domicilio g = repo.save(d);
        auditoria.registrar("EDITAR_DOMICILIO", "Domicilio", g.getNumero(), "Total $" + g.getTotal().toPlainString());
        return g;
    }

    public Domicilio cancelar(String id, String motivo) {
        Domicilio d = obtener(id);
        exigirAntesDelDespacho(d, "cancelar");
        if (motivo == null || motivo.trim().length() < 5) {
            throw new CampoInvalidoException("motivo", "Indique el motivo de la cancelación (mínimo 5 caracteres)");
        }
        String m = recortar(motivo.trim(), 200);
        pedidos.anular(d.getPedidoId(), "Domicilio " + d.getNumero() + " cancelado: " + m);
        EstadoDomicilio antes = d.getEstado();
        d.setMotivoCancelacion(m);
        d.cambiarEstado(EstadoDomicilio.CANCELADO, UsuarioActual.responsableActual(), m);
        Domicilio g = repo.save(d);
        auditar(g, antes, m);
        return g;
    }

    // ------------------------------------------------------------------ HU-048 · asignar

    /** AC-048.1 y AC-048.2 (RN-14). Se puede reasignar mientras siga ASIGNADO. */
    public Domicilio asignar(String id, String domiciliario) {
        Domicilio d = obtener(id);
        sincronizarCocina(List.of(d));
        if (EstadoDomicilio.EN_COCINA.contains(d.getEstado())) {
            throw new ReglaNegocioException(PEDIDO_NO_LISTO, PEDIDO_NO_LISTO + ": el pedido " + d.getPedidoNumero()
                    + " del domicilio " + d.getNumero() + " aún está en cocina (" + d.getEstado().etiqueta().toLowerCase() + ")");
        }
        if (d.getEstado() != EstadoDomicilio.LISTO && d.getEstado() != EstadoDomicilio.ASIGNADO) {
            throw transicionInvalida(d, EstadoDomicilio.ASIGNADO);
        }
        Domiciliario elegido = domiciliarios().stream().filter(x -> x.username().equals(domiciliario)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException(DOMICILIARIO_NO_DISPONIBLE, DOMICILIARIO_NO_DISPONIBLE
                        + ": «" + domiciliario + "» no es un domiciliario activo"));
        if (!elegido.disponible()) {
            throw new ReglaNegocioException(DOMICILIARIO_NO_DISPONIBLE, DOMICILIARIO_NO_DISPONIBLE + ": "
                    + nombre(elegido) + " está en camino con otra entrega");
        }
        EstadoDomicilio antes = d.getEstado();
        d.setDomiciliario(elegido.username());
        d.setDomiciliarioNombre(elegido.nombre());
        d.setAsignadoEn(Instant.now());
        d.cambiarEstado(EstadoDomicilio.ASIGNADO, UsuarioActual.responsableActual(), "Asignado a " + nombre(elegido));
        Domicilio g = repo.save(d);
        auditar(g, antes, "Domiciliario " + elegido.username());
        return g;
    }

    // ------------------------------------------------------------------ HU-049/050/052 · domiciliario

    /** HU-049: el domiciliario sale con el pedido. */
    public Domicilio enCamino(String id, UsuarioActual u) {
        Domicilio d = obtenerPara(id, u);
        if (d.getEstado() != EstadoDomicilio.ASIGNADO) {
            throw transicionInvalida(d, EstadoDomicilio.EN_CAMINO);
        }
        d.setEnCaminoEn(Instant.now());
        d.cambiarEstado(EstadoDomicilio.EN_CAMINO, u.responsable(), "Salió del restaurante");
        Domicilio g = repo.save(d);
        auditar(g, EstadoDomicilio.ASIGNADO, null);
        return g;
    }

    /**
     * HU-049 y HU-050: entrega con cobro contra entrega. Primero se valida el pago aquí (sin efectos), luego
     * el pedido pasa a ENTREGADO en pedidos-service y facturación emite el comprobante a nombre del cajero que
     * registró el domicilio. Si algo falla, el domicilio sigue EN_CAMINO y se puede reintentar: las dos
     * llamadas son idempotentes.
     */
    public Domicilio entregar(String id, CobroRequest cobro, UsuarioActual u) {
        Domicilio d = obtenerPara(id, u);
        if (d.getEstado() != EstadoDomicilio.EN_CAMINO) {
            throw transicionInvalida(d, EstadoDomicilio.ENTREGADO);
        }
        if (cobro == null || cobro.metodo() == null || cobro.monto() == null) {
            throw new CampoInvalidoException("monto", "Registre el método y el monto recibido");
        }
        BigDecimal monto = pesos(cobro.monto());
        if (monto.compareTo(d.getTotal()) < 0) {
            throw new ReglaNegocioException(PAGO_INSUFICIENTE, PAGO_INSUFICIENTE + ": recibió $" + monto.toPlainString()
                    + " y el domicilio " + d.getNumero() + " cuesta $" + d.getTotal().toPlainString());
        }
        if (cobro.metodo() != MetodoCobro.EFECTIVO && monto.compareTo(d.getTotal()) > 0) {
            throw new CampoInvalidoException("monto", "Con tarjeta se cobra el valor exacto ($" + d.getTotal().toPlainString() + ")");
        }
        String referencia = cobro.referencia() == null || cobro.referencia().isBlank() ? null : recortar(cobro.referencia().trim(), 60);

        pedidos.entregar(d.getPedidoId());
        FacturacionClient.Comprobante f = facturacion.cobrar(new FacturacionClient.CobroDomicilioRequest(d.getId(),
                d.getNumero(), d.getPedidoId(), d.getValorDomicilio(), d.getRegistradoPor(), d.getRegistradoPorNombre(),
                u.responsable(), u.nombre(), List.of(new FacturacionClient.Pago(cobro.metodo().name(), monto, referencia))));

        if (f.total() != null && f.total().compareTo(d.getTotal()) != 0) {
            // facturación es la fuente del total: si cambió el impuesto entre el registro y el cobro, manda el comprobante
            log.warn("Domicilio {}: el comprobante {} quedó por ${} y el domicilio tenía ${}", d.getNumero(), f.numero(),
                    f.total(), d.getTotal());
            d.setTotal(f.total());
        }
        d.setCobro(new Domicilio.Cobro(cobro.metodo().name(), f.totalRecibido() != null ? f.totalRecibido() : monto,
                f.cambio() != null ? f.cambio() : monto.subtract(d.getTotal()), f.id(), f.numero(), referencia, Instant.now()));
        d.setEntregadoEn(Instant.now());
        d.cambiarEstado(EstadoDomicilio.ENTREGADO, u.responsable(), "Cobrado $" + d.getCobro().recibido().toPlainString()
                + " en " + cobro.metodo().etiqueta().toLowerCase() + " · comprobante " + f.numero()
                + (d.getCobro().cambio().signum() > 0 ? " · cambio $" + d.getCobro().cambio().toPlainString() : ""));
        Domicilio g = repo.save(d);
        auditar(g, EstadoDomicilio.EN_CAMINO, "Comprobante " + f.numero() + " · $" + g.getTotal().toPlainString());
        return g;
    }

    /** HU-052 (RN-17): el cliente no estaba. El pedido se anula sin factura y la anulación queda auditada. */
    public Domicilio noEntregado(String id, String motivo, UsuarioActual u) {
        Domicilio d = obtenerPara(id, u);
        if (motivo == null || motivo.trim().length() < MOTIVO_MINIMO) {
            throw new ReglaNegocioException(MOTIVO_OBLIGATORIO, MOTIVO_OBLIGATORIO + ": indique por qué no se entregó (mínimo "
                    + MOTIVO_MINIMO + " caracteres)");
        }
        if (d.getEstado() != EstadoDomicilio.EN_CAMINO) {
            throw transicionInvalida(d, EstadoDomicilio.NO_ENTREGADO);
        }
        String m = recortar(motivo.trim(), 200);
        pedidos.anular(d.getPedidoId(), "Domicilio " + d.getNumero() + " no entregado: " + m);
        d.setMotivoNoEntrega(m);
        d.cambiarEstado(EstadoDomicilio.NO_ENTREGADO, u.responsable(), m + " · anulado sin factura; el pedido vuelve al restaurante");
        Domicilio g = repo.save(d);
        auditar(g, EstadoDomicilio.EN_CAMINO, m);
        return g;
    }

    // ------------------------------------------------------------------ cocina

    /**
     * Trae el avance de cocina de los domicilios que siguen ahí (RECIBIDO o EN_PREPARACION) con una sola
     * llamada a pedidos-service. Si pedidos-service no responde, se muestran como estaban.
     */
    public void sincronizarCocina(List<Domicilio> lista) {
        List<Domicilio> enCocina = lista.stream().filter(d -> EstadoDomicilio.EN_COCINA.contains(d.getEstado())
                && d.getPedidoId() != null).toList();
        if (enCocina.isEmpty()) {
            return;
        }
        Map<String, String> estados;
        try {
            estados = pedidos.estados(enCocina.stream().map(Domicilio::getPedidoId).toList());
        } catch (WokException ex) {
            log.warn("No se pudo consultar el avance de cocina: {}", ex.getMessage());
            return;
        }
        for (Domicilio d : enCocina) {
            EstadoDomicilio nuevo = desdeCocina(estados.get(d.getPedidoId()));
            if (nuevo == null || nuevo == d.getEstado()) {
                continue;
            }
            EstadoDomicilio antes = d.getEstado();
            d.cambiarEstado(nuevo, "cocina", "Avance de cocina (pedido " + d.getPedidoNumero() + ")");
            try {
                Domicilio g = repo.save(d);
                d.setVersion(g.getVersion());
                auditar(g, antes, "Avance de cocina");
            } catch (OptimisticLockingFailureException ex) {
                // otra pantalla lo actualizó a la vez: se toma el estado guardado
                repo.findById(d.getId()).ifPresent(x -> {
                    d.setEstado(x.getEstado());
                    d.setVersion(x.getVersion());
                });
            }
        }
    }

    static EstadoDomicilio desdeCocina(String estadoPedido) {
        if (estadoPedido == null) {
            return null;
        }
        return switch (estadoPedido) {
            case "ABIERTO", "ENVIADO" -> EstadoDomicilio.RECIBIDO;
            case "EN_PREPARACION" -> EstadoDomicilio.EN_PREPARACION;
            case "LISTO" -> EstadoDomicilio.LISTO;
            case "CANCELADO" -> EstadoDomicilio.CANCELADO;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ apoyo

    /** RN-12: platos + impuesto al consumo sobre los platos (mismo redondeo que facturación) + domicilio. */
    public static BigDecimal[] totales(BigDecimal subtotalPlatos, BigDecimal porcentajeImpuesto, BigDecimal valorDomicilio) {
        BigDecimal sub = pesos(subtotalPlatos == null ? BigDecimal.ZERO : subtotalPlatos);
        BigDecimal pct = porcentajeImpuesto == null ? BigDecimal.ZERO : porcentajeImpuesto;
        BigDecimal impuesto = pesos(sub.multiply(pct).divide(CIEN, 4, RoundingMode.HALF_UP));
        BigDecimal dom = pesos(valorDomicilio == null ? BigDecimal.ZERO : valorDomicilio);
        return new BigDecimal[] { impuesto, sub.add(impuesto).add(dom) };
    }

    private static void recalcularTotal(Domicilio d) {
        BigDecimal[] t = totales(d.getSubtotalPlatos(), d.getImpuestoPorcentaje(), d.getValorDomicilio());
        d.setImpuestoValor(t[0]);
        d.setTotal(t[1]);
    }

    /** AC-047.2: indica el campo que falta o está mal. */
    static Domicilio.Cliente validarCliente(String nombre, String telefono, String direccion, String referencia) {
        if (nombre == null || nombre.isBlank()) {
            throw new CampoInvalidoException("cliente", "El nombre del cliente es obligatorio");
        }
        String tel = normalizarTelefono(telefono);
        if (tel == null) {
            throw new CampoInvalidoException("telefono", DomicilioDtos.MENSAJE_TELEFONO);
        }
        if (direccion == null || direccion.isBlank()) {
            throw new CampoInvalidoException("direccion", DomicilioDtos.MENSAJE_DIRECCION);
        }
        return new Domicilio.Cliente(recortar(nombre.trim(), 80), tel, recortar(direccion.trim(), 150),
                referencia == null || referencia.isBlank() ? null : recortar(referencia.trim(), 150));
    }

    /** Deja solo los 10 dígitos (quita espacios, guiones, puntos y el indicativo +57). Null si no son 10. */
    public static String normalizarTelefono(String telefono) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.length() == 12 && digitos.startsWith("57")) {
            digitos = digitos.substring(2);
        }
        return digitos.length() == 10 ? digitos : null;
    }

    private static BigDecimal validarValor(BigDecimal v) {
        if (v == null || v.signum() < 0) {
            throw new CampoInvalidoException("valorDomicilio", "Indique el valor del domicilio (cero o más)");
        }
        return pesos(v);
    }

    private static void exigirAntesDelDespacho(Domicilio d, String accion) {
        if (!d.getEstado().antesDelDespacho()) {
            throw new ReglaNegocioException(YA_DESPACHADO, YA_DESPACHADO + ": el domicilio " + d.getNumero()
                    + " está " + d.getEstado().etiqueta().toLowerCase() + "; ya no se puede " + accion);
        }
    }

    private static ReglaNegocioException transicionInvalida(Domicilio d, EstadoDomicilio destino) {
        String ayuda = destino == EstadoDomicilio.ENTREGADO && d.getEstado() == EstadoDomicilio.ASIGNADO
                ? "; márquelo primero «en camino»" : "";
        return new ReglaNegocioException(TRANSICION_INVALIDA, TRANSICION_INVALIDA + ": el domicilio " + d.getNumero()
                + " está " + d.getEstado().etiqueta().toLowerCase() + " y no puede pasar a «"
                + destino.etiqueta().toLowerCase() + "»" + ayuda);
    }

    /** RN-18 · AC-053.2: auditoría central del cambio de estado, sin datos personales del cliente. */
    private void auditar(Domicilio d, EstadoDomicilio antes, String detalle) {
        String cambio = (antes == null ? "—" : antes.name()) + " → " + d.getEstado().name();
        auditoria.registrar("ESTADO_DOMICILIO", "Domicilio", d.getNumero(), detalle == null ? cambio : cambio + " · " + detalle);
    }

    private void anularPedidoSinFallar(String pedidoId, String motivo) {
        try {
            pedidos.anular(pedidoId, motivo);
        } catch (RuntimeException ex) {
            log.error("No se pudo anular el pedido {} de un domicilio que no se guardó: {}", pedidoId, ex.getMessage());
        }
    }

    private static String nombre(Domiciliario d) {
        return d.nombre() == null || d.nombre().isBlank() ? d.username() : d.nombre();
    }

    static BigDecimal pesos(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
