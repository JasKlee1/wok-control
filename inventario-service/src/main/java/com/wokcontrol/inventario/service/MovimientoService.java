package com.wokcontrol.inventario.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.inventario.dto.InventarioDtos.AjusteRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.MovimientoRequest;
import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.MovimientoInventario;
import com.wokcontrol.inventario.entity.MovimientoInventario.Tipo;
import com.wokcontrol.inventario.repository.MovimientoRepository;

/** Movimientos manuales del responsable de inventario y consulta del historial. */
@Service
public class MovimientoService {

    private final ExistenciasService existencias;
    private final MovimientoRepository repo;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;
    private final ZoneId zona;

    public MovimientoService(ExistenciasService existencias, MovimientoRepository repo, MongoTemplate mongo,
            AuditoriaPublisher auditoria, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.existencias = existencias;
        this.repo = repo;
        this.mongo = mongo;
        this.auditoria = auditoria;
        this.zona = ZoneId.of(zona);
    }

    public MovimientoInventario registrar(String insumoId, MovimientoRequest req) {
        String usuario = UsuarioActual.responsableActual();
        String motivo = req.motivo() == null ? null : req.motivo().trim();
        ExistenciasService.Aplicado r = switch (req.tipo()) {
            case ENTRADA -> {
                String lote = req.lote() == null || req.lote().isBlank() ? null : req.lote().trim();
                ExistenciasService.Aplicado a = existencias.aumentar(insumoId, req.cantidad(), Tipo.ENTRADA,
                        motivo == null || motivo.isBlank() ? "Entrada manual" : motivo, usuario, "MANUAL",
                        req.costoUnitario(), lote, req.fechaVencimiento());
                if (lote != null) {
                    mongo.updateFirst(Query.query(Criteria.where("_id").is(insumoId)),
                            new Update().push("lotes", new Insumo.Lote(lote, req.cantidad(), req.fechaVencimiento(),
                                    java.time.Instant.now(), "MANUAL", false)), Insumo.class);
                }
                yield a;
            }
            case SALIDA, MERMA -> {
                if (motivo == null || motivo.length() < 5) {
                    throw new ReglaNegocioException("Indique el motivo de la " + (req.tipo().name().equals("MERMA") ? "merma" : "salida")
                            + " (mínimo 5 caracteres)");
                }
                yield existencias.disminuir(insumoId, req.cantidad(), Tipo.valueOf(req.tipo().name()), motivo, usuario, "MANUAL");
            }
        };
        auditoria.registrar(req.tipo().name(), "Insumo", insumoId,
                req.cantidad().stripTrailingZeros().toPlainString() + " " + r.despues().getUnidad() + (motivo != null ? " · " + motivo : ""));
        return r.movimiento();
    }

    public MovimientoInventario ajustar(String insumoId, AjusteRequest req) {
        ExistenciasService.Aplicado r = existencias.ajustar(insumoId, req.cantidadContada(), req.motivo().trim(),
                UsuarioActual.responsableActual());
        auditoria.registrar("AJUSTE", "Insumo", insumoId, r.movimiento().getTipo() + " "
                + r.movimiento().getCantidad().stripTrailingZeros().toPlainString() + " · " + req.motivo());
        return r.movimiento();
    }

    public Page<MovimientoInventario> buscar(String insumoId, Tipo tipo, String referencia, LocalDate desde,
            LocalDate hasta, Pageable pageable) {
        Query q = new Query();
        if (insumoId != null && !insumoId.isBlank()) {
            q.addCriteria(Criteria.where("insumoId").is(insumoId));
        }
        if (tipo != null) {
            q.addCriteria(Criteria.where("tipo").is(tipo));
        }
        if (referencia != null && !referencia.isBlank()) {
            q.addCriteria(Criteria.where("referencia").is(referencia.trim()));
        }
        if (desde != null || hasta != null) {
            Criteria f = Criteria.where("fecha");
            if (desde != null) {
                f = f.gte(desde.atStartOfDay(zona).toInstant());
            }
            if (hasta != null) {
                f = f.lt(hasta.plusDays(1).atStartOfDay(zona).toInstant());
            }
            q.addCriteria(f);
        }
        long total = mongo.count(q, MovimientoInventario.class);
        q.with(pageable).with(Sort.by(Sort.Direction.DESC, "fecha"));
        List<MovimientoInventario> lista = mongo.find(q, MovimientoInventario.class);
        return new PageImpl<>(lista, pageable, total);
    }

    public List<MovimientoInventario> recientes(String insumoId) {
        return repo.findTop20ByInsumoIdOrderByFechaDesc(insumoId);
    }
}
