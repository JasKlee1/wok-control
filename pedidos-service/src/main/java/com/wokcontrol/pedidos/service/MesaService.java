package com.wokcontrol.pedidos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** v2 · AC-036.2: al cobrar la última cuenta de la mesa, vencen las sesiones de los clientes. */
    public void cerrarSesionesSiLibre(String mesaId) {
        if (!tienePedidosActivos(mesaId)) {
            sesiones.cerrarDeMesa(mesaId, "Cuenta cobrada");
        }
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
        return listar(incluirInactivas).stream().map(m -> new MesaResponse(m.getId(), m.getNumero(), m.getCapacidad(),
                m.getZona(), m.getEstado(), m.isActiva(), m.getOcupadaDesde(),
                conteo.getOrDefault(m.getId(), new int[1])[0], consumo.getOrDefault(m.getId(), BigDecimal.ZERO),
                m.getCuentaSolicitadaEn(), sinEntregar.getOrDefault(m.getId(), 0))).toList();
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
        m.setEstado(fuera ? Mesa.Estado.FUERA_DE_SERVICIO : Mesa.Estado.DISPONIBLE);
        m.setOcupadaDesde(null);
        return repo.save(m);
    }

    /** Libera manualmente una mesa ocupada solo si ya no tiene pedidos activos. */
    public Mesa liberar(String id) {
        Mesa m = obtener(id);
        if (tienePedidosActivos(id)) {
            throw new ReglaNegocioException("La mesa " + m.getNumero() + " tiene pedidos sin pagar o en curso; no se puede liberar");
        }
        actualizarOcupacion(id);
        sesiones.cerrarDeMesa(id, "Mesa liberada");
        auditoria.registrar("LIBERAR", "Mesa", id, "Mesa " + m.getNumero());
        return obtener(id);
    }

    public boolean tienePedidosActivos(String mesaId) {
        return pedidos.countByMesaIdAndEstadoIn(mesaId, EstadoPedido.ACTIVOS) > 0;
    }

    /** Recalcula la ocupación según los pedidos activos (no altera mesas fuera de servicio). */
    public void actualizarOcupacion(String mesaId) {
        boolean ocupada = tienePedidosActivos(mesaId);
        Query q = Query.query(Criteria.where("_id").is(mesaId).and("estado").ne(Mesa.Estado.FUERA_DE_SERVICIO));
        if (ocupada) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(mesaId).and("estado").is(Mesa.Estado.DISPONIBLE)),
                    new Update().set("estado", Mesa.Estado.OCUPADA).set("ocupadaDesde", Instant.now()), Mesa.class);
        } else {
            mongo.updateFirst(q, new Update().set("estado", Mesa.Estado.DISPONIBLE).unset("ocupadaDesde"), Mesa.class);
        }
    }

    private static void validarZona(String zona) {
        if (!PedidoDtos.ZONAS.contains(zona)) {
            throw new ReglaNegocioException("Zona inválida: " + zona);
        }
    }
}
