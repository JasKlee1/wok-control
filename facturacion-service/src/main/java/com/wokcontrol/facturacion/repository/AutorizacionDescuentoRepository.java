package com.wokcontrol.facturacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.facturacion.entity.AutorizacionDescuento;

public interface AutorizacionDescuentoRepository extends MongoRepository<AutorizacionDescuento, String> {

    Optional<AutorizacionDescuento> findByCodigo(String codigo);

    List<AutorizacionDescuento> findTop50ByOrderByCreadaEnDesc();
}
