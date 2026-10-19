package com.wokcontrol.proveedores.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.proveedores.entity.OrdenCompra;

public interface OrdenCompraRepository extends MongoRepository<OrdenCompra, String> {

    List<OrdenCompra> findByProveedorIdOrderByCreadoEnDesc(String proveedorId);

    List<OrdenCompra> findByProveedorIdAndEstadoNotOrderByCreadoEnDesc(String proveedorId, OrdenCompra.Estado estado);

    List<OrdenCompra> findByEstadoInOrderByCreadoEnDesc(Collection<OrdenCompra.Estado> estados);

    List<OrdenCompra> findAllByOrderByCreadoEnDesc();
}
