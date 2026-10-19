package com.wokcontrol.menu.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.menu.entity.Plato;

public interface PlatoRepository extends MongoRepository<Plato, String> {

    boolean existsByNombreIgnoreCase(String nombre);

    List<Plato> findAllByOrderByNombreAsc();

    List<Plato> findByActivoTrueOrderByNombreAsc();

    long countByCategoriaIdAndActivoTrue(String categoriaId);
}
