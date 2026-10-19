package com.wokcontrol.usuarios.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.usuarios.entity.ParametrosGenerales;

public interface ParametrosRepository extends MongoRepository<ParametrosGenerales, String> {
}
