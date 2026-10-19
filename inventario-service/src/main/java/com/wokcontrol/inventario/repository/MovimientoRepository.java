package com.wokcontrol.inventario.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.inventario.entity.MovimientoInventario;

/** Los movimientos solo se insertan; nunca se actualizan ni eliminan. */
public interface MovimientoRepository extends MongoRepository<MovimientoInventario, String> {

    List<MovimientoInventario> findTop20ByInsumoIdOrderByFechaDesc(String insumoId);

    List<MovimientoInventario> findByReferenciaOrderByFechaAsc(String referencia);
}
