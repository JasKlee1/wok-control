package com.wokcontrol.inventario.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.inventario.entity.Insumo;

public interface InsumoRepository extends MongoRepository<Insumo, String> {

    boolean existsByNombreIgnoreCase(String nombre);

    List<Insumo> findByActivoTrueOrderByNombreAsc();
}
