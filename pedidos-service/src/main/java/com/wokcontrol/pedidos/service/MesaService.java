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

    public MesaService(MesaRepository repo, PedidoRepository pedidos, MongoTemplate mongo, AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.pedidos = pedidos;
        this.mongo = mongo;
        this.auditoria = auditoria;
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
        for (Pedido p : pedidos.findByEstadoInOrderByCreadoEnAsc(EstadoPedido.ACTIVOS)) {
            conteo.computeIfAbsent(p.getMesaId(), k -> new int[1])[0]++;
            consumo.merge(p.getMesaId(), p.getTotal(), BigDecimal::add);
        }
        return listar(incluirInactivas).stream().map(m -> new MesaResponse(m.getId(), m.getNumero(), m.getCapacidad(),
                m.getZona(), m.getEstado(), m.isActiva(), m.getOcupadaDesde(),
                conteo.getOrDefault(m.getId(), new int[1])[0], consumo.getOrDefault(m.getId(), BigDecimal.ZERO))).toList();
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
