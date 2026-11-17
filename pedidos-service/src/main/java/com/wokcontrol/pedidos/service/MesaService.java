package com.wokcontrol.pedidos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.pedidos.dto.PedidoDtos;
import com.wokcontrol.pedidos.dto.PedidoDtos.MesaRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.MesaResponse;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.repository.MesaRepository;
import com.wokcontrol.pedidos.repository.PedidoRepository;

@Service
public class MesaService {

    private final MesaRepository repo;
    private final PedidoRepository pedidos;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;
    private final SesionMesaService sesiones;

    public MesaService(MesaRepository repo, PedidoRepository pedidos, MongoTemplate mongo, AuditoriaPublisher auditoria,
            SesionMesaService sesiones) {
        this.repo = repo;
        this.pedidos = pedidos;
        this.mongo = mongo;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
    }

    /**
     * v2 · HU-033: código del QR de la mesa. Se genera la primera vez que se pide, es aleatorio y no
     * cambia, para que el QR impreso siga sirviendo.
     */
    public String codigoQr(String id) {
        Mesa m = obtener(id);
        if (m.getCodigoQr() == null || m.getCodigoQr().isBlank()) {
            m.setCodigoQr(java.util.UUID.randomUUID().toString().replace("-", ""));
            repo.save(m);
            auditoria.registrar("GENERAR_QR", "Mesa", id, "QR de la mesa " + m.getNumero());
        }
        return m.getCodigoQr();
    }

    /**
     * v2 · HU-044 (REQ-WOK-044) y AC-036.2: al cobrar la última cuenta de la mesa (y de su unión), la
     * mesa pasa a POR_LIMPIAR, la unión se separa y vencen las sesiones QR de los clientes.
     */
    public void alCobrar(String mesaId) {
        dejarPorLimpiar(obtener(mesaId), "Cuenta cobrada");
    }

    /**
     * v2 · HU-044 (RN-08): el grupo se fue. Si la mesa y las de su unión ya no tienen pedidos activos,
     * todas pasan a POR_LIMPIAR. Devuelve las mesas que cambiaron (vacío si aún hay pedidos).
     */
    List<Mesa> dejarPorLimpiar(Mesa mesa, String motivo) {
        List<Mesa> grupo = mesasDeLaUnion(mesa);
        if (grupo.stream().anyMatch(m -> tienePedidosActivos(m.getId()))) {
            return List.of();
        }
        Instant ahora = Instant.now();
        List<Mesa> cambiadas = new ArrayList<>();
        for (Mesa m : grupo) {
            if (m.getEstado() != Mesa.Estado.FUERA_DE_SERVICIO) {
                m.setEstado(Mesa.Estado.POR_LIMPIAR);
                m.setPorLimpiarDesde(ahora);
            }
            m.setOcupadaDesde(null);
            m.setAsignacion(null);
            cambiadas.add(repo.save(m));
            sesiones.cerrarDeMesa(m.getId(), motivo);
        }
        auditoria.registrar("MESA_POR_LIMPIAR", "Mesa", mesa.getId(), motivo + " · mesa(s) "
                + cambiadas.stream().map(m -> String.valueOf(m.getNumero())).collect(Collectors.joining(", ")));
        return cambiadas;
    }

    /** v2 · HU-045: la mesa y las demás de su unión, si la tiene. */
    public List<Mesa> mesasDeLaUnion(Mesa mesa) {
        if (mesa.getAsignacion() == null || !mesa.getAsignacion().esUnion()) {
            return List.of(mesa);
        }
        List<Mesa> union = repo.findByAsignacionUnionId(mesa.getAsignacion().unionId());
        return union.isEmpty() ? List.of(mesa) : union;
    }

    /** v2 · HU-044 (AC-044.2): la mesa ya está limpia y vuelve a estar libre para sentar a otro grupo. */
    public Mesa marcarLimpia(String id) {
        Mesa m = obtener(id);
        if (m.getEstado() != Mesa.Estado.POR_LIMPIAR) {
            throw new ReglaNegocioException("La mesa " + m.getNumero() + " no está por limpiar (estado: "
                    + m.getEstado().etiqueta().toLowerCase() + ")");
        }
        m.setEstado(Mesa.Estado.DISPONIBLE);
        m.setPorLimpiarDesde(null);
        Mesa g = repo.save(m);
        auditoria.registrar("MARCAR_LIMPIA", "Mesa", id, "Mesa " + m.getNumero());
        return g;
    }

    public List<Mesa> listar(boolean incluirInactivas) {
        return incluirInactivas ? repo.findAllByOrderByNumeroAsc() : repo.findByActivaTrueOrderByNumeroAsc();
    }

    public Mesa obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Mesa", id));
    }

    /** Mesas con el número de pedidos activos y el consumo acumulado de cada una. */
    public List<MesaResponse> tablero(boolean incluirInactivas) {
        Map<String, int[]> conteo = new HashMap<>();
        Map<String, BigDecimal> consumo = new HashMap<>();
        Map<String, Integer> sinEntregar = new HashMap<>();
        for (Pedido p : pedidos.findByEstadoInOrderByCreadoEnAsc(EstadoPedido.ACTIVOS)) {
            conteo.computeIfAbsent(p.getMesaId(), k -> new int[1])[0]++;
            consumo.merge(p.getMesaId(), p.getTotal(), BigDecimal::add);
            sinEntregar.merge(p.getMesaId(), platosSinEntregar(p), Integer::sum);
        }
        return listar(incluirInactivas).stream().map(m -> respuesta(m, conteo.getOrDefault(m.getId(), new int[1])[0],
                consumo.getOrDefault(m.getId(), BigDecimal.ZERO), sinEntregar.getOrDefault(m.getId(), 0))).toList();
    }

    /** Respuesta de una mesa con su ocupación; v2 · HU-042 agrega el grupo, el mesero y la unión. */
    public static MesaResponse respuesta(Mesa m, int pedidosActivos, BigDecimal consumo, int sinEntregar) {
        Mesa.Asignacion a = m.getAsignacion();
        return new MesaResponse(m.getId(), m.getNumero(), m.getCapacidad(), m.getZona(), m.getEstado(), m.isActiva(),
                m.getOcupadaDesde(), pedidosActivos, consumo, m.getCuentaSolicitadaEn(), sinEntregar,
                a == null ? null : a.grupo(), a == null ? 0 : a.personas(), a == null ? null : a.mesero(),
                a == null ? null : a.meseroNombre(), a == null ? null : a.unionId(),
                a == null || a.unionNumeros() == null ? List.of() : a.unionNumeros(), m.getPorLimpiarDesde());
    }

    /** v2 · AC-035.2: unidades de un pedido activo que aún no llegan a la mesa. */
    public static int platosSinEntregar(Pedido p) {
        return p.getItems().stream().filter(i -> i.getEstado() != com.wokcontrol.pedidos.entity.ItemPedido.Estado.ENTREGADO)
                .mapToInt(com.wokcontrol.pedidos.entity.ItemPedido::getCantidad).sum();
    }

    /**
     * v2 · HU-035 (REQ-WOK-035): el cliente pidió la cuenta. La mesa queda como «Cuenta solicitada»
     * para el cajero hasta que se cobre o se libere.
     */
    public void solicitarCuenta(String mesaId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(mesaId)), new Update().set("cuentaSolicitadaEn", Instant.now()),
                Mesa.class);
    }

    public Mesa crear(MesaRequest req) {
        validarZona(req.zona());
        if (repo.existsByNumero(req.numero())) {
            throw new ConflictoException("Ya existe la mesa " + req.numero());
        }
        Mesa m = new Mesa();
        m.setNumero(req.numero());
        m.setCapacidad(req.capacidad());
        m.setZona(req.zona());
        m.setActiva(req.activa());
        m.setEstado(Mesa.Estado.DISPONIBLE);
        Mesa g = repo.save(m);
        auditoria.registrar("CREAR", "Mesa", g.getId(), "Mesa " + g.getNumero());
        return g;
    }

    public Mesa actualizar(String id, MesaRequest req) {
        validarZona(req.zona());
        Mesa m = obtener(id);
        if (m.getNumero() != req.numero() && repo.existsByNumero(req.numero())) {
            throw new ConflictoException("Ya existe la mesa " + req.numero());
        }
        if (!req.activa() && tienePedidosActivos(id)) {
            throw new ReglaNegocioException("No se puede desactivar una mesa con pedidos activos");
        }
        if (!req.activa() && m.getAsignacion() != null) {
            throw new ReglaNegocioException("No se puede desactivar una mesa con un grupo sentado");
        }
        m.setNumero(req.numero());
        m.setCapacidad(req.capacidad());
        m.setZona(req.zona());
        m.setActiva(req.activa());
        Mesa g = repo.save(m);
        auditoria.registrar("ACTUALIZAR", "Mesa", id, "Mesa " + g.getNumero());
        return g;
    }

    public Mesa fueraDeServicio(String id, boolean fuera) {
        Mesa m = obtener(id);
        if (fuera && tienePedidosActivos(id)) {
            throw new ReglaNegocioException("La mesa tiene pedidos activos");
        }
        if (fuera && m.getAsignacion() != null) {
            throw new ReglaNegocioException("La mesa tiene un grupo sentado");
        }
        m.setEstado(fuera ? Mesa.Estado.FUERA_DE_SERVICIO : Mesa.Estado.DISPONIBLE);
        m.setOcupadaDesde(null);
        m.setPorLimpiarDesde(null);
        return repo.save(m);
    }

    /**
     * Libera manualmente una mesa ocupada solo si ya no tiene pedidos activos (ni las de su unión).
     * v2 · HU-044 (RN-08): el grupo se fue, así que la mesa pasa a POR_LIMPIAR.
     */
    public Mesa liberar(String id) {
        Mesa m = obtener(id);
        for (Mesa x : mesasDeLaUnion(m)) {
            if (tienePedidosActivos(x.getId())) {
                throw new ReglaNegocioException("La mesa " + x.getNumero() + " tiene pedidos sin pagar o en curso; no se puede liberar");
            }
        }
        if (m.getEstado() == Mesa.Estado.OCUPADA) {
            dejarPorLimpiar(m, "Mesa liberada");
        } else {
            sesiones.cerrarDeMesa(id, "Mesa liberada");
        }
        auditoria.registrar("LIBERAR", "Mesa", id, "Mesa " + m.getNumero());
        return obtener(id);
    }

    public boolean tienePedidosActivos(String mesaId) {
        return pedidos.countByMesaIdAndEstadoIn(mesaId, EstadoPedido.ACTIVOS) > 0;
    }

    /**
     * Recalcula la ocupación según los pedidos activos (no altera mesas fuera de servicio).
     * <ul>
     * <li>Con pedidos activos, una mesa libre o por limpiar pasa a OCUPADA.</li>
     * <li>v2 · HU-042/044: sin pedidos activos, solo vuelve a libre una mesa OCUPADA sin grupo sentado
     * (cuando se cancela su único pedido). Una mesa con grupo sigue ocupada y una por limpiar sigue
     * por limpiar hasta que alguien la marque limpia (RN-08).</li>
     * </ul>
     */
    public void actualizarOcupacion(String mesaId) {
        boolean ocupada = tienePedidosActivos(mesaId);
        if (ocupada) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(mesaId)
                    .and("estado").in(Mesa.Estado.DISPONIBLE, Mesa.Estado.POR_LIMPIAR)),
                    new Update().set("estado", Mesa.Estado.OCUPADA).set("ocupadaDesde", Instant.now()).unset("porLimpiarDesde"),
                    Mesa.class);
        } else {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(mesaId).and("estado").is(Mesa.Estado.OCUPADA)
                    .and("asignacion").exists(false)),
                    new Update().set("estado", Mesa.Estado.DISPONIBLE).unset("ocupadaDesde"), Mesa.class);
        }
    }

    private static void validarZona(String zona) {
        if (!PedidoDtos.ZONAS.contains(zona)) {
            throw new ReglaNegocioException("Zona inválida: " + zona);
        }
    }
}
