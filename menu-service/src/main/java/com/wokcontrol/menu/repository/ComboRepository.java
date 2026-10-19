package com.wokcontrol.menu.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.menu.entity.Combo;

public interface ComboRepository extends MongoRepository<Combo, String> {

    boolean existsByNombreIgnoreCase(String nombre);

    List<Combo> findAllByOrderByNombreAsc();

    List<Combo> findByItemsPlatoId(String platoId);
}
