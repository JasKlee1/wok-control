package com.wokcontrol.inventario.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.inventario.entity.ReporteAgotado;

public interface ReporteAgotadoRepository extends MongoRepository<ReporteAgotado, String> {

    List<ReporteAgotado> findByAtendidoFalseOrderByFechaDesc();

    List<ReporteAgotado> findTop20ByAtendidoTrueOrderByAtendidoEnDesc();
}
