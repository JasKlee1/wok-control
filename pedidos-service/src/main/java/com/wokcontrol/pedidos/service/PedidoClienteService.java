package com.wokcontrol.pedidos.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizado;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizar;
import com.wokcontrol.pedidos.dto.PedidoDtos.ItemRequest;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.entity.SesionMesa.LineaCarrito;
import com.wokcontrol.pedidos.repository.PedidoRepository;
import com.wokcontrol.pedidos.repository.SesionMesaRepository;

/**
 * v2 · Épica 7 · Lo que el cliente hace desde su celular con la sesión de mesa:
 * <ul>
 * <li>T006 · HU-032 (REQ-WOK-032): arma su pedido antes de enviarlo (agregar, cambiar cantidad u
 * observación, quitar). Un pedido ya enviado solo lo cambia el mesero (RN-02).</li>
 * <li>T008 · HU-034 (REQ-WOK-034): lo envía en POR_CONFIRMAR y sigue el estado de cada plato.</li>
 * <li>T012 · HU-038 (REQ-WOK-038): si un plato se agota antes de enviar, se marca, se avisa y no se
 * deja enviar hasta quitarlo (RN-04).</li>
 * <li>T014 · HU-035 (REQ-WOK-035): pide la cuenta; el cajero ve la mesa como «Cuenta solicitada».</li>
 * </ul>
 * El pedido sin enviar vive en la sesión de mesa: no es un pedido todavía, así que no ocupa la mesa
 * ni aparece al mesero ni a cocina.
 */
@Service
public class PedidoClienteService {

    /** AC-032.2 */
    public static final String MENSAJE_YA_ENVIADO = "El pedido ya fue enviado; pide el cambio al mesero";
    /** AC-038.2 */
    public static final String MENSAJE_HAY_AGOTADOS = "Quita los platos agotados antes de enviar";
    /** AC-036.1 */
    public static final String MENSAJE_OTRA_MESA = "Ese pedido no es de tu mesa";

    static final int MAX_UNIDADES = 20;
    static final int MAX_NOTAS = 200;

    public record LineaVista(String id, String nombre, int cantidad, String notas, BigDecimal precioUnitario,
            BigDecimal subtotal, boolean agotado, String motivoAgotado) {
    }

    /** Pedido sin enviar con la disponibilidad actual de cada plato. */
    public record Carrito(List<LineaVista> lineas, BigDecimal total, int unidades, boolean hayAgotados) {
        public boolean vacio() {
            return lineas.isEmpty();
        }
    }

    public record CuentaSolicitada(int mesaNumero, BigDecimal total, int platosSinEntregar) {
    }

    private final SesionMesaRepository sesiones;
    private final PedidoService pedidos;
    private final PedidoRepository pedidosRepo;
    private final MesaService mesas;
    private final MenuClient menu;
    private final AuditoriaPublisher auditoria;

    public PedidoClienteService(SesionMesaRepository sesiones, PedidoService pedidos, PedidoRepository pedidosRepo,
            MesaService mesas, MenuClient menu, AuditoriaPublisher auditoria) {
        this.sesiones = sesiones;
        this.pedidos = pedidos;
        this.pedidosRepo = pedidosRepo;
        this.mesas = mesas;
        this.menu = menu;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------ HU-032 · pedido sin enviar

    /** Agrega un plato de la carta. Si ya está con la misma observación, suma la cantidad. */
    public SesionMesa agregar(SesionMesa s, String tipo, String productoId, int cantidad) {
        if (!"PLATO".equals(tipo) && !"COMBO".equals(tipo)) {
            throw new ReglaNegocioException("Producto desconocido");
        }
        if (productoId == null || productoId.isBlank()) {
            throw new ReglaNegocioException("Producto desconocido");
        }
        int unidades = validarCantidad(cantidad);
        ItemCotizado c = menu.cotizar(List.of(new ItemCotizar(tipo, productoId, unidades))).items().get(0);
        if (!c.disponible()) {
            throw new ReglaNegocioException(c.nombre() + " está agotado en este momento");
        }
        LineaCarrito existente = s.getCarrito().stream()
                .filter(l -> l.getProductoId().equals(productoId) && l.getTipo().equals(tipo) && l.getNotas() == null)
                .findFirst().orElse(null);
        if (existente != null) {
            existente.setCantidad(validarCantidad(existente.getCantidad() + unidades));
            existente.setPrecioUnitario(c.precioUnitario());
        } else {
            LineaCarrito l = new LineaCarrito();
            l.setTipo(tipo);
            l.setProductoId(productoId);
            l.setNombre(c.nombre());
            l.setPrecioUnitario(c.precioUnitario());
            l.setCantidad(unidades);
            l.setNivelPicante(Math.max(0, Math.min(3, c.picanteBase())));
            s.getCarrito().add(l);
        }
        return sesiones.save(s);
    }

    /** AC-032.1: cambia la cantidad y la observación; con cantidad 0 el plato se quita. */
    public SesionMesa cambiar(SesionMesa s, String lineaId, int cantidad, String notas) {
        LineaCarrito l = linea(s, lineaId);
        if (cantidad <= 0) {
            s.getCarrito().remove(l);
        } else {
            l.setCantidad(validarCantidad(cantidad));
            l.setNotas(limpiar(notas));
        }
        return sesiones.save(s);
    }

    public SesionMesa quitar(SesionMesa s, String lineaId) {
        s.getCarrito().remove(linea(s, lineaId));
        return sesiones.save(s);
    }

    /**
     * AC-032.2 · RN-02: un pedido que ya salió del celular solo lo cambia el mesero. Antes se
     * comprueba que el pedido sea de la mesa de la sesión (AC-036.1).
     */
    public void modificarEnviado(SesionMesa s, String pedidoId) {
        Pedido p = pedidos.obtener(pedidoId);
        if (!s.getMesaId().equals(p.getMesaId())) {
            throw new OperacionNoPermitidaException(MENSAJE_OTRA_MESA);
        }
        throw new ReglaNegocioException(MENSAJE_YA_ENVIADO);
    }

    /**
     * Pedido sin enviar con la disponibilidad que hoy informa menu-service (HU-038, AC-038.1): si un
     * plato se agotó después de agregarlo, queda marcado y no suma al total.
     */
    public Carrito carrito(SesionMesa s) {
        List<LineaCarrito> lineas = s.getCarrito();
        if (lineas.isEmpty()) {
            return new Carrito(List.of(), BigDecimal.ZERO, 0, false);
        }
        List<ItemCotizado> cotizados = menu.cotizar(lineas.stream()
                .map(l -> new ItemCotizar(l.getTipo(), l.getProductoId(), l.getCantidad())).toList()).items();
        List<LineaVista> vista = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int unidades = 0;
        boolean hayAgotados = false;
        for (int k = 0; k < lineas.size(); k++) {
            LineaCarrito l = lineas.get(k);
            ItemCotizado c = cotizados.get(k);
            boolean agotado = !c.disponible();
            BigDecimal precio = c.precioUnitario() != null ? c.precioUnitario() : l.getPrecioUnitario();
            BigDecimal subtotal = precio == null ? BigDecimal.ZERO : precio.multiply(BigDecimal.valueOf(l.getCantidad()));
            vista.add(new LineaVista(l.getId(), l.getNombre(), l.getCantidad(), l.getNotas(), precio, subtotal, agotado,
                    agotado ? c.motivoNoDisponible() : null));
            if (agotado) {
                hayAgotados = true;
            } else {
                total = total.add(subtotal);
                unidades += l.getCantidad();
            }
        }
        return new Carrito(vista, total, unidades, hayAgotados);
    }

    // ------------------------------------------------------------------ HU-034 · enviar y seguir

    /**
     * AC-034.1: el pedido queda POR_CONFIRMAR y el mesero lo ve. AC-034.3: sin platos se rechaza.
     * AC-038.2: con platos agotados se rechaza.
     */
    public Pedido enviar(SesionMesa s) {
        if (s.getCarrito().isEmpty()) {
            throw new ReglaNegocioException(PedidoService.MENSAJE_PEDIDO_VACIO);
        }
        if (carrito(s).hayAgotados()) {
            throw new ReglaNegocioException(MENSAJE_HAY_AGOTADOS);
        }
        List<ItemRequest> items = s.getCarrito().stream()
                .map(l -> new ItemRequest(ItemPedido.Tipo.valueOf(l.getTipo()), l.getProductoId(), l.getCantidad(),
                        l.getNivelPicante(), l.getNotas()))
                .toList();
        Pedido p = pedidos.crearDesdeQr(s.getMesaId(), s.getId(), items);
        s.getCarrito().clear();
        sesiones.save(s);
        return p;
    }

    /**
     * Pedidos QR de la cuenta actual de la mesa, con su estado y el de cada plato (AC-034.2). Incluye
     * los de todas las sesiones ACTIVAS de la mesa: si el cliente vuelve a escanear o otro comensal
     * escanea el mismo QR, ve lo que ya pidió la mesa. Las sesiones se cierran al cobrar, así que
     * nunca se ven pedidos de clientes anteriores (AC-036.1, AC-036.2).
     */
    public List<Pedido> pedidosDeSesion(SesionMesa s) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        ids.add(s.getId());
        sesiones.findByMesaIdAndEstado(s.getMesaId(), SesionMesa.Estado.ACTIVA).forEach(x -> ids.add(x.getId()));
        return pedidosRepo.findBySesionMesaIdInOrderByCreadoEnAsc(ids).stream()
                .filter(p -> s.getMesaId().equals(p.getMesaId()))
                .toList();
    }

    /** Lo que el cliente ve de cada plato enviado: por confirmar, en cocina, preparando, listo o entregado. */
    public static String estadoPlato(Pedido p, ItemPedido i) {
        if (p.getEstado() == EstadoPedido.POR_CONFIRMAR) {
            return "Por confirmar";
        }
        if (p.getEstado() == EstadoPedido.RECHAZADO) {
            return "Rechazado";
        }
        if (p.getEstado() == EstadoPedido.CANCELADO) {
            return "Cancelado";
        }
        return switch (i.getEstado()) {
            case PENDIENTE -> "En cocina";
            case EN_PREPARACION -> "En preparación";
            case LISTO -> "Listo";
            case ENTREGADO -> "Entregado";
        };
    }

    // ------------------------------------------------------------------ HU-035 · pedir la cuenta

    /**
     * AC-035.1: la mesa queda como «Cuenta solicitada» para el cajero, con el total. AC-035.2: si
     * hay platos sin entregar, igual se registra y el cajero ve el aviso.
     */
    public CuentaSolicitada pedirCuenta(SesionMesa s) {
        List<Pedido> activos = pedidos.activosDeMesa(s.getMesaId());
        if (activos.isEmpty()) {
            throw new ReglaNegocioException("Tu mesa aún no tiene pedidos para cobrar");
        }
        BigDecimal total = activos.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int sinEntregar = activos.stream().mapToInt(MesaService::platosSinEntregar).sum();
        mesas.solicitarCuenta(s.getMesaId());
        auditoria.registrar("SOLICITAR_CUENTA", "Mesa", s.getMesaId(), "Mesa " + s.getMesaNumero()
                + (sinEntregar > 0 ? " · " + sinEntregar + " plato(s) sin entregar" : ""));
        return new CuentaSolicitada(s.getMesaNumero(), total, sinEntregar);
    }

    public boolean cuentaSolicitada(SesionMesa s) {
        Mesa m = mesas.obtener(s.getMesaId());
        return m.getCuentaSolicitadaEn() != null;
    }

    // ------------------------------------------------------------------ actualización del celular

    /**
     * Huella de lo que el cliente ve en «Mi pedido». El celular la consulta cada pocos segundos y
     * recarga solo si cambió: así ve el plato agotado (AC-038.1), el avance de cocina (AC-034.2) o el
     * rechazo del mesero (AC-037.2) sin recargar a mano.
     */
    public String huella(SesionMesa s) {
        StringBuilder sb = new StringBuilder();
        if (!s.getCarrito().isEmpty()) {
            carrito(s).lineas().forEach(l -> sb.append(l.id()).append(l.cantidad()).append(l.agotado()).append(';'));
        }
        for (Pedido p : pedidosDeSesion(s)) {
            sb.append(p.getId()).append(p.getEstado());
            p.getItems().forEach(i -> sb.append(i.getEstado().ordinal()));
            sb.append('|');
        }
        sb.append(cuentaSolicitada(s));
        return Integer.toHexString(Objects.hash(sb.toString()));
    }

    // ------------------------------------------------------------------ apoyo

    private static LineaCarrito linea(SesionMesa s, String lineaId) {
        return s.getCarrito().stream().filter(l -> l.getId().equals(lineaId)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Ese plato ya no está en tu pedido"));
    }

    private static int validarCantidad(int cantidad) {
        if (cantidad < 1) {
            throw new ReglaNegocioException("La cantidad mínima es 1");
        }
        if (cantidad > MAX_UNIDADES) {
            throw new ReglaNegocioException("Máximo " + MAX_UNIDADES + " unidades por plato; para más, llama al mesero");
        }
        return cantidad;
    }

    private static String limpiar(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > MAX_NOTAS ? t.substring(0, MAX_NOTAS) : t;
    }
}
