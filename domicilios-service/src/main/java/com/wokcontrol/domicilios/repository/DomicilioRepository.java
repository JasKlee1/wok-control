package com.wokcontrol.domicilios.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.entity.EstadoDomicilio;

public interface DomicilioRepository extends MongoRepository<Domicilio, String> {

    List<Domicilio> findByEstadoInOrderByCreadoEnAsc(Collection<EstadoDomicilio> estados);

    List<Domicilio> findTop30ByEstadoInOrderByCerradoEnDesc(Collection<EstadoDomicilio> estados);

    /** AC-049.1: lo que tiene asignado un domiciliario. */
    List<Domicilio> findByDomiciliarioAndEstadoInOrderByAsignadoEnAsc(String domiciliario, Collection<EstadoDomicilio> estados);

    long countByDomiciliarioAndEstado(String domiciliario, EstadoDomicilio estado);
}
