package com.wokcontrol.usuarios.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.usuarios.entity.RegistroAuditoria;

/** Solo se insertan y consultan registros; la consulta con filtros se hace en AuditoriaService. */
public interface AuditoriaRepository extends MongoRepository<RegistroAuditoria, String> {
}
