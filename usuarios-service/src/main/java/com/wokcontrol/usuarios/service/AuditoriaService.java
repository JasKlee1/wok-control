package com.wokcontrol.usuarios.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.EventoAuditoria;
import com.wokcontrol.usuarios.entity.RegistroAuditoria;
import com.wokcontrol.usuarios.repository.AuditoriaRepository;

@Service
public class AuditoriaService {

    private final AuditoriaRepository repo;
    private final MongoTemplate mongo;
    private final ZoneId zona;

    public AuditoriaService(AuditoriaRepository repo, MongoTemplate mongo,
            @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.repo = repo;
        this.mongo = mongo;
        this.zona = ZoneId.of(zona);
    }

    public void registrar(EventoAuditoria e) {
        repo.save(new RegistroAuditoria(e.servicio(), e.usuario(), e.accion(), e.entidad(), e.entidadId(), e.detalle(),
                e.fecha() == null ? Instant.now() : e.fecha()));
    }

    public Page<RegistroAuditoria> buscar(String usuario, String servicio, String texto, LocalDate desde,
            LocalDate hasta, Pageable pageable) {
        Query q = new Query();
        if (usuario != null && !usuario.isBlank()) {
            q.addCriteria(Criteria.where("usuario").is(usuario.trim()));
        }
        if (servicio != null && !servicio.isBlank()) {
            q.addCriteria(Criteria.where("servicio").is(servicio));
        }
        if (texto != null && !texto.isBlank()) {
            String rx = ".*" + Pattern.quote(texto.trim()) + ".*";
            q.addCriteria(new Criteria().orOperator(Criteria.where("accion").regex(rx, "i"),
                    Criteria.where("entidad").regex(rx, "i"), Criteria.where("entidadId").regex(rx, "i"),
                    Criteria.where("detalle").regex(rx, "i")));
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
        long total = mongo.count(q, RegistroAuditoria.class);
        q.with(pageable).with(Sort.by(Sort.Direction.DESC, "fecha"));
        List<RegistroAuditoria> lista = mongo.find(q, RegistroAuditoria.class);
        return new PageImpl<>(lista, pageable, total);
    }
}
