package com.wokcontrol.common.mongo;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Consecutivos atómicos (número de pedido, comprobante, orden de compra) en la colección
 * "secuencias" de la base de datos del propio servicio.
 */
@Service
public class SecuenciaService {

    private final MongoTemplate mongo;

    public SecuenciaService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public long siguiente(String nombre) {
        Secuencia s = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(nombre)),
                new Update().inc("valor", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Secuencia.class, "secuencias");
        return s == null ? 1 : s.valor();
    }

    public String siguienteFormateado(String nombre, String prefijo) {
        return prefijo + String.format("%06d", siguiente(nombre));
    }

    record Secuencia(String id, long valor) {
    }
}
