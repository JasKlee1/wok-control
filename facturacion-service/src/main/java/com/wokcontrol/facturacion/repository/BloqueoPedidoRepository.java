package com.wokcontrol.facturacion.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.facturacion.entity.BloqueoPedido;

public interface BloqueoPedidoRepository extends MongoRepository<BloqueoPedido, String> {
}
