package com.wokcontrol.usuarios.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.usuarios.entity.Rol;

public interface RolRepository extends MongoRepository<Rol, String> {

    List<Rol> findAllByOrderBySistemaDescNombreAsc();
}
