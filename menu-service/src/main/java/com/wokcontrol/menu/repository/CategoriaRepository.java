package com.wokcontrol.menu.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.menu.entity.Categoria;

public interface CategoriaRepository extends MongoRepository<Categoria, String> {

    boolean existsByNombreIgnoreCase(String nombre);

    List<Categoria> findAllByOrderByOrdenAscNombreAsc();
}
