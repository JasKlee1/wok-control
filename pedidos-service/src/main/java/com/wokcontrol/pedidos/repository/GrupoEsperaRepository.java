package com.wokcontrol.pedidos.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.pedidos.entity.GrupoEspera;

public interface GrupoEsperaRepository extends MongoRepository<GrupoEspera, String> {

    /** v2 · HU-040: la lista de espera, en orden de llegada (RN-10). */
    List<GrupoEspera> findByEstadoOrderByLlegadaEnAsc(GrupoEspera.Estado estado);
}
