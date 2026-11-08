package com.wokcontrol.pedidos.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.pedidos.entity.SesionMesa;

public interface SesionMesaRepository extends MongoRepository<SesionMesa, String> {

    long countByMesaIdAndEstado(String mesaId, SesionMesa.Estado estado);
}
