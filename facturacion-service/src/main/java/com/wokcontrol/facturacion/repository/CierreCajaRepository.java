package com.wokcontrol.facturacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.facturacion.entity.CierreCaja;

public interface CierreCajaRepository extends MongoRepository<CierreCaja, String> {

    List<CierreCaja> findTop100ByOrderByCreadoEnDesc();

    Optional<CierreCaja> findFirstByCajeroOrderByCreadoEnDesc(String cajero);
}
