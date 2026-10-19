package com.wokcontrol.pedidos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
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
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.pedidos.client.InventarioClient;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizado;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizar;
import com.wokcontrol.pedidos.dto.PedidoDtos;
import com.wokcontrol.pedidos.dto.PedidoDtos.ItemRequest;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.repository.PedidoRepository;

/**
 * Ciclo de vida del pedido. Reglas principales:
 * <ul>
 * <li>Al menos un producto; precio, alérgenos y receta se copian de menu-service al registrar.</li>
 * <li>Solo se modifica mientras está ABIERTO (antes de enviarlo a cocina).</li>
 * <li>Al enviarlo a cocina se descuenta inventario con referencia PEDIDO-id (idempotente). Si falta
 * stock o inventario no responde, el pedido sigue ABIERTO y no se envía.</li>
 * <li>Se cancela solo antes de que cocina empiece a prepararlo; si ya se había descontado
 * inventario, se revierte (también idempotente). Se registra quién y por qué.</li>
 * <li>Solo facturación (token de servicio) lo marca como PAGADO.</li>
 * </ul>
 */
@Service
public class PedidoService {

    private final PedidoRepository repo;
    private final MesaService mesas;
    private final MenuClient menu;
    private final InventarioClient inventario;
    private final SecuenciaService secuencias;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;
    private final ZoneId zona;

    public PedidoService(PedidoRepository repo, MesaService mesas, MenuClient menu, InventarioClient inventario,
            SecuenciaService secuencias, MongoTemplate mongo, AuditoriaPublisher auditoria,
            @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.repo = repo;
        this.mesas = mesas;
        this.menu = menu;
        this.inventario = inventario;
        this.secuencias = secuencias;
        this.mongo = mongo;
        this.auditoria = auditoria;
        this.zona = ZoneId.of(zona);
    }

    public Pedido obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Pedido", id));
    }

    public List<Pedido> buscar(EstadoPedido estado, String mesaId, LocalDate fecha, String mesero) {
        Query q = new Query();
        if (estado != null) {
            q.addCriteria(Criteria.where("estado").is(estado));
        }
        if (mesaId != null && !mesaId.isBlank()) {
            q.addCriteria(Criteria.where("mesaId").is(mesaId));
        }
        if (mesero != null && !mesero.isBlank()) {
            q.addCriteria(Criteria.where("mesero").is(mesero));
        }
        if (fecha != null) {
            q.addCriteria(Criteria.where("creadoEn").gte(fecha.atStartOfDay(zona).toInstant())
                    .lt(fecha.plusDays(1).atStartOfDay(zona).toInstant()));
        }
        q.with(Sort.by(Sort.Direction.DESC, "creadoEn")).limit(300);
        return mongo.find(q, Pedido.class);
    }

    public List<Pedido> activosDeMesa(String mesaId) {
        return repo.findByMesaIdAndEstadoInOrderByCreadoEnAsc(mesaId, EstadoPedido.ACTIVOS);
    }

    /** Pedidos entregados y aún sin pagar: son los que facturación puede cobrar. */
    public List<Pedido> facturables(String mesaId) {
        return repo.findByMesaIdAndEstadoInOrderByCreadoEnAsc(mesaId, Set.of(EstadoPedido.ENTREGADO));
    }

    public List<Pedido> comandasCocina() {
        return repo.findByEstadoInOrderByEnviadoEnAsc(EstadoPedido.EN_COCINA);
    }

    // ------------------------------------------------------------------ registro y edición

    public Pedido crear(PedidoDtos.PedidoRequest req) {
        Mesa mesa = mesas.obtener(req.mesaId());
        if (!mesa.isActiva() || mesa.getEstado() == Mesa.Estado.FUERA_DE_SERVICIO) {
            throw new ReglaNegocioException("La mesa " + mesa.getNumero() + " no está disponible para recibir pedidos");
        }
        UsuarioActual u = UsuarioActual.requerido();
        Pedido p = new Pedido();
        p.setNumero(secuencias.siguienteFormateado("pedidos", "P-"));
        p.setMesaId(mesa.getId());
        p.setMesaNumero(mesa.getNumero());
        p.setMesero(u.responsable());
        p.setMeseroNombre(u.nombre());
        p.setItems(construirItems(req.items()));
        p.setObservaciones(limpiar(req.observaciones()));
        p.setAlergias(validarAlergias(req.alergias()));
        p.setEstado(EstadoPedido.ABIERTO);
        p.registrar(EstadoPedido.ABIERTO.name(), u.responsable(), "Pedido registrado");
        Pedido g = repo.save(p);
        mesas.actualizarOcupacion(mesa.getId());
        auditoria.registrar("CREAR", "Pedido", g.getNumero(), "Mesa " + mesa.getNumero() + " · " + g.getUnidades() + " productos");
        return g;
    }

    public Pedido actualizar(String id, PedidoDtos.ActualizarPedidoRequest req) {
        Pedido p = obtener(id);
        if (!p.getEstado().permiteEditarItems()) {
            throw new ReglaNegocioException("El pedido " + p.getNumero() + " ya fue enviado a cocina y no se puede modificar");
        }
        p.setItems(construirItems(req.items()));
        p.setObservaciones(limpiar(req.observaciones()));
        p.setAlergias(validarAlergias(req.alergias()));
        p.registrar("MODIFICADO", UsuarioActual.responsableActual(), p.getUnidades() + " productos");
        return repo.save(p);
    }

    /** Copia precios, alérgenos y receta vigentes desde menu-service y valida disponibilidad. */
    private List<ItemPedido> construirItems(List<ItemRequest> solicitados) {
        if (solicitados == null || solicitados.isEmpty()) {
            throw new ReglaNegocioException("El pedido debe tener al menos un producto");
        }
        List<ItemCotizar> cotizar = solicitados.stream()
                .map(i -> new ItemCotizar(i.tipo().name(), i.productoId(), i.cantidad())).toList();
        List<ItemCotizado> cotizados = menu.cotizar(cotizar).items();
        List<String> noDisponibles = new ArrayList<>();
        List<ItemPedido> items = new ArrayList<>();
        for (int k = 0; k < solicitados.size(); k++) {
            ItemRequest s = solicitados.get(k);
            ItemCotizado c = cotizados.get(k);
            if (!c.id().equals(s.productoId())) {
                throw new ReglaNegocioException("La cotización de menu-service no corresponde al pedido");
            }
            if (!c.disponible()) {
                noDisponibles.add(c.nombre() + " (" + c.motivoNoDisponible() + ")");
                continue;
            }
            if (c.precioUnitario() == null || c.precioUnitario().signum() <= 0) {
                throw new ReglaNegocioException("El producto " + c.nombre() + " no tiene un precio válido");
            }
            ItemPedido it = new ItemPedido();
            it.setTipo(s.tipo());
            it.setProductoId(c.id());
            it.setNombre(c.nombre());
            it.setPrecioUnitario(c.precioUnitario());
            it.setCantidad(s.cantidad());
            it.setNivelPicante(s.nivelPicante());
            it.setNotas(limpiar(s.notas()));
            it.setAlergenos(c.alergenos() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(c.alergenos()));
            it.setReceta(c.receta() == null ? List.of() : c.receta().stream()
                    .map(r -> new ItemPedido.ConsumoInsumo(r.insumoId(), r.nombreInsumo(), r.unidad(), r.cantidadPorUnidad()))
                    .toList());
            it.setActualizadoEn(Instant.now());
            items.add(it);
        }
        if (!noDisponibles.isEmpty()) {
            throw new ReglaNegocioException("Productos no disponibles: " + String.join(", ", noDisponibles));
        }
        return items;
    }

    // ------------------------------------------------------------------ estados

    public Pedido enviarACocina(String id) {
        Pedido p = obtener(id);
        if (p.getEstado() != EstadoPedido.ABIERTO) {
            throw new ReglaNegocioException("Solo se envían a cocina pedidos abiertos (estado actual: " + p.getEstado().etiqueta() + ")");
        }
        if (p.getItems().isEmpty()) {
            throw new ReglaNegocioException("El pedido no tiene productos");
        }
        List<InventarioClient.LineaConsumo> lineas = lineasConsumo(p);
        if (!lineas.isEmpty()) {
            // idempotente: si esta llamada se reintenta, inventario no descuenta dos veces
            inventario.consumir(p.referenciaInventario(), "Pedido " + p.getNumero() + " · mesa " + p.getMesaNumero(), lineas);
            p.setConsumoInventario(Pedido.EstadoConsumo.APLICADO);
        }
        p.setEstado(EstadoPedido.ENVIADO);
        p.setEnviadoEn(Instant.now());
        p.registrar(EstadoPedido.ENVIADO.name(), UsuarioActual.responsableActual(),
                lineas.isEmpty() ? "Sin receta: no descuenta inventario" : "Inventario descontado (" + lineas.size() + " insumos)");
        Pedido g = repo.save(p);
        auditoria.registrar("ENVIAR_COCINA", "Pedido", g.getNumero(), null);
        return g;
    }

    static List<InventarioClient.LineaConsumo> lineasConsumo(Pedido p) {
        Map<String, BigDecimal> porInsumo = new LinkedHashMap<>();
        for (ItemPedido it : p.getItems()) {
            for (ItemPedido.ConsumoInsumo r : it.getReceta()) {
                porInsumo.merge(r.insumoId(), r.cantidadPorUnidad().multiply(BigDecimal.valueOf(it.getCantidad())),
                        BigDecimal::add);
            }
        }
        return porInsumo.entrySet().stream().map(e -> new InventarioClient.LineaConsumo(e.getKey(), e.getValue())).toList();
    }

    public Pedido cancelar(String id, String motivo) {
        Pedido p = obtener(id);
        if (!p.getEstado().permiteCancelar()) {
            throw new ReglaNegocioException("El pedido está " + p.getEstado().etiqueta().toLowerCase()
                    + ": ya no se puede cancelar");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new ReglaNegocioException("Indique el motivo de la cancelación (mínimo 5 caracteres)");
        }
        if (p.getEstado() == EstadoPedido.ENVIADO
                && p.getItems().stream().anyMatch(i -> i.getEstado() != ItemPedido.Estado.PENDIENTE)) {
            throw new ReglaNegocioException("Cocina ya empezó a preparar este pedido; no se puede cancelar");
        }
        if (p.getConsumoInventario() == Pedido.EstadoConsumo.APLICADO) {
            inventario.revertir(p.referenciaInventario());
            p.setConsumoInventario(Pedido.EstadoConsumo.REVERTIDO);
        }
        String usuario = UsuarioActual.responsableActual();
        p.setEstado(EstadoPedido.CANCELADO);
        p.setCanceladoEn(Instant.now());
        p.setCanceladoPor(usuario);
        p.setMotivoCancelacion(motivo.trim());
        p.registrar(EstadoPedido.CANCELADO.name(), usuario, motivo.trim());
        Pedido g = repo.save(p);
        mesas.actualizarOcupacion(p.getMesaId());
        auditoria.registrar("CANCELAR", "Pedido", g.getNumero(), motivo.trim());
        return g;
    }

    /**
     * Avance de un ítem. Cocina pasa PENDIENTE → EN_PREPARACION → LISTO; el mesero pasa LISTO →
     * ENTREGADO. El estado del pedido se recalcula a partir de sus ítems.
     */
    public Pedido cambiarEstadoItem(String pedidoId, String itemId, ItemPedido.Estado destino) {
        Pedido p = obtener(pedidoId);
        if (!EstadoPedido.EN_COCINA.contains(p.getEstado())) {
            throw new ReglaNegocioException("El pedido no está en cocina (estado: " + p.getEstado().etiqueta() + ")");
        }
        UsuarioActual u = UsuarioActual.requerido();
        boolean esCocina = destino == ItemPedido.Estado.EN_PREPARACION || destino == ItemPedido.Estado.LISTO;
        if (esCocina && !u.tiene(Permisos.COCINA_OPERAR)) {
            throw new OperacionNoPermitidaException("Solo cocina puede cambiar el estado de preparación");
        }
        if (destino == ItemPedido.Estado.ENTREGADO && !u.tiene(Permisos.PEDIDOS_GESTIONAR)) {
            throw new OperacionNoPermitidaException("Solo el mesero registra la entrega en la mesa");
        }
        ItemPedido item = p.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Ítem", itemId));
        if (!item.getEstado().puedePasarA(destino)) {
            throw new ReglaNegocioException("Transición inválida: " + item.getEstado().etiqueta() + " → " + destino.etiqueta());
        }
        item.setEstado(destino);
        item.setActualizadoEn(Instant.now());
        EstadoPedido antes = p.getEstado();
        p.recalcularEstado();
        p.registrar(destino.name(), u.responsable(), item.getCantidad() + " × " + item.getNombre());
        Pedido g = repo.save(p);
        if (antes != g.getEstado()) {
            auditoria.registrar("ESTADO_" + g.getEstado().name(), "Pedido", g.getNumero(), null);
        }
        return g;
    }

    /** Avanza todos los ítems que estén en el estado previo (atajo de cocina y de entrega). */
    public Pedido avanzarTodos(String pedidoId, ItemPedido.Estado destino) {
        Pedido p = obtener(pedidoId);
        Pedido resultado = p;
        for (ItemPedido it : p.getItems()) {
            if (it.getEstado().puedePasarA(destino)) {
                resultado = cambiarEstadoItem(pedidoId, it.getId(), destino);
            }
        }
        if (resultado == p) {
            throw new ReglaNegocioException("No hay productos que puedan pasar a " + destino.etiqueta());
        }
        return resultado;
    }

    /** Llamado por facturacion-service al emitir el comprobante. Idempotente para la misma factura. */
    public Pedido marcarPagado(String id, String facturaId) {
        Pedido p = obtener(id);
        if (p.getEstado() == EstadoPedido.PAGADO) {
            if (facturaId.equals(p.getFacturaId())) {
                return p;
            }
            throw new ConflictoException("El pedido " + p.getNumero() + " ya fue pagado con otro comprobante");
        }
        if (p.getEstado() != EstadoPedido.ENTREGADO) {
            throw new ReglaNegocioException("Solo se cobran pedidos entregados (estado actual: " + p.getEstado().etiqueta() + ")");
        }
        p.setEstado(EstadoPedido.PAGADO);
        p.setPagadoEn(Instant.now());
        p.setFacturaId(facturaId);
        p.registrar(EstadoPedido.PAGADO.name(), UsuarioActual.responsableActual(), "Comprobante " + facturaId);
        Pedido g = repo.save(p);
        mesas.actualizarOcupacion(p.getMesaId());
        return g;
    }

    /** Llamado por facturación al anular un comprobante: el pedido vuelve a quedar por cobrar. */
    public Pedido revertirPago(String id, String facturaId) {
        Pedido p = obtener(id);
        if (p.getEstado() == EstadoPedido.ENTREGADO && p.getFacturaId() == null) {
            return p;
        }
        if (p.getEstado() != EstadoPedido.PAGADO || !facturaId.equals(p.getFacturaId())) {
            throw new ConflictoException("El pedido no está pagado con el comprobante indicado");
        }
        p.setEstado(EstadoPedido.ENTREGADO);
        p.setPagadoEn(null);
        p.setFacturaId(null);
        p.registrar("PAGO_ANULADO", UsuarioActual.responsableActual(), "Comprobante " + facturaId + " anulado");
        Pedido g = repo.save(p);
        mesas.actualizarOcupacion(p.getMesaId());
        return g;
    }

    private static Set<String> validarAlergias(Set<String> alergias) {
        Set<String> r = new LinkedHashSet<>();
        if (alergias != null) {
            for (String a : alergias) {
                if (!PedidoDtos.ALERGIAS.contains(a)) {
                    throw new ReglaNegocioException("Alergia desconocida: " + a);
                }
                r.add(a);
            }
        }
        return r;
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
