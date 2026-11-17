package com.wokcontrol.pedidos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.pedidos.entity.Mesa;

public interface MesaRepository extends MongoRepository<Mesa, String> {

    boolean existsByNumero(int numero);

    List<Mesa> findAllByOrderByNumeroAsc();

    List<Mesa> findByActivaTrueOrderByNumeroAsc();

    Optional<Mesa> findByCodigoQr(String codigoQr);

    /** v2 · HU-045: las mesas de una misma unión. */
    List<Mesa> findByAsignacionUnionId(String unionId);
}
