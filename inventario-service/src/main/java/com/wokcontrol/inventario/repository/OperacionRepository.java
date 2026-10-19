package com.wokcontrol.inventario.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.inventario.entity.OperacionIdempotente;

public interface OperacionRepository extends MongoRepository<OperacionIdempotente, String> {
}
