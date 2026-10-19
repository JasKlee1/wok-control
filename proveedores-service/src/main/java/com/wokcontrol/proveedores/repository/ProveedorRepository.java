package com.wokcontrol.proveedores.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.proveedores.entity.Proveedor;

public interface ProveedorRepository extends MongoRepository<Proveedor, String> {

    Optional<Proveedor> findByNit(String nit);

    boolean existsByNit(String nit);

    List<Proveedor> findAllByOrderByNombreComercialAsc();

    List<Proveedor> findByInsumosInsumoIdOrderByNombreComercialAsc(String insumoId);
}
