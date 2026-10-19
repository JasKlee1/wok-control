package com.wokcontrol.facturacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.facturacion.entity.Factura;

public interface FacturaRepository extends MongoRepository<Factura, String> {

    Optional<Factura> findByClaveIdempotencia(String clave);

    List<Factura> findByCajeroAndCierreIdIsNullOrderByEmitidaEnAsc(String cajero);

    List<Factura> findBySincronizacionIn(List<Factura.Sincronizacion> estados);

    List<Factura> findByCierreIdOrderByEmitidaEnAsc(String cierreId);
}
