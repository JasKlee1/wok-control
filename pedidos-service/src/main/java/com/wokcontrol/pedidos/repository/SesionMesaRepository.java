package com.wokcontrol.pedidos.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.pedidos.entity.SesionMesa;

public interface SesionMesaRepository extends MongoRepository<SesionMesa, String> {

    long countByMesaIdAndEstado(String mesaId, SesionMesa.Estado estado);

    /** v2 · HU-034: sesiones abiertas de la mesa (varios celulares de la misma cuenta). */
    java.util.List<SesionMesa> findByMesaIdAndEstado(String mesaId, SesionMesa.Estado estado);
}
