package com.wokcontrol.inventario.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.MovimientoInventario;
import com.wokcontrol.inventario.entity.MovimientoInventario.Tipo;
import com.wokcontrol.inventario.repository.InsumoRepository;
import com.wokcontrol.inventario.repository.MovimientoRepository;

/**
 * Único punto que modifica cantidades. Cada cambio es una operación atómica de MongoDB
 * (findAndModify con $inc): las salidas incluyen la condición "cantidad >= requerido", de modo que
 * dos operaciones concurrentes no pueden dejar existencias negativas. Cada cambio genera su
 * movimiento con saldo anterior y nuevo.
 */
@Service
public class ExistenciasService {

    private final MongoTemplate mongo;
    private final InsumoRepository insumos;
    private final MovimientoRepository movimientos;

    public ExistenciasService(MongoTemplate mongo, InsumoRepository insumos, MovimientoRepository movimientos) {
        this.mongo = mongo;
        this.insumos = insumos;
        this.movimientos = movimientos;
    }

    public record Aplicado(Insumo despues, MovimientoInventario movimiento) {
    }

    public Aplicado aumentar(String insumoId, BigDecimal cantidad, Tipo tipo, String motivo, String usuario,
            String referencia, BigDecimal costoUnitario, String lote, LocalDate vencimiento) {
        validarCantidad(cantidad);
        Insumo despues = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(insumoId).and("activo").is(true)),
                new Update().inc("cantidadDisponible", cantidad).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), Insumo.class);
        if (despues == null) {
            throw noEncontradoOInactivo(insumoId);
        }
        if (costoUnitario != null && costoUnitario.signum() > 0) {
            actualizarCostoPromedio(despues, cantidad, costoUnitario);
        }
        MovimientoInventario m = movimientos.save(new MovimientoInventario(despues, tipo, cantidad, motivo, usuario,
                referencia, costoUnitario, lote, vencimiento));
        return new Aplicado(despues, m);
    }

    public Aplicado disminuir(String insumoId, BigDecimal cantidad, Tipo tipo, String motivo, String usuario,
            String referencia) {
        validarCantidad(cantidad);
        Insumo despues = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(insumoId).and("activo").is(true)
                        .and("cantidadDisponible").gte(cantidad)),
                new Update().inc("cantidadDisponible", cantidad.negate()).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), Insumo.class);
        if (despues == null) {
            Insumo actual = insumos.findById(insumoId).orElseThrow(() -> new RecursoNoEncontradoException("Insumo", insumoId));
            if (!actual.isActivo()) {
                throw noEncontradoOInactivo(insumoId);
            }
            throw new StockInsuficienteException(actual.getNombre(), actual.getCantidadDisponible(), cantidad, actual.getUnidad());
        }
        MovimientoInventario m = movimientos.save(new MovimientoInventario(despues, tipo, cantidad, motivo, usuario,
                referencia, despues.getCostoUnitario(), null, null));
        return new Aplicado(despues, m);
    }

    /**
     * Ajuste por conteo físico. Se aplica solo si la cantidad no cambió desde que se leyó; si otro
     * movimiento ocurrió en medio, se vuelve a leer y se recalcula (hasta 3 intentos).
     */
    public Aplicado ajustar(String insumoId, BigDecimal contada, String motivo, String usuario) {
        if (contada == null || contada.signum() < 0) {
            throw new ReglaNegocioException("La cantidad contada no puede ser negativa");
        }
        for (int intento = 0; intento < 3; intento++) {
            Insumo actual = insumos.findById(insumoId).orElseThrow(() -> new RecursoNoEncontradoException("Insumo", insumoId));
            if (!actual.isActivo()) {
                throw noEncontradoOInactivo(insumoId);
            }
            BigDecimal diferencia = contada.subtract(actual.getCantidadDisponible());
            if (diferencia.signum() == 0) {
                throw new ReglaNegocioException("La cantidad contada coincide con el sistema; no hay nada que ajustar");
            }
            Insumo despues = mongo.findAndModify(
                    Query.query(Criteria.where("_id").is(insumoId).and("cantidadDisponible").is(actual.getCantidadDisponible())),
                    new Update().set("cantidadDisponible", contada).inc("version", 1),
                    FindAndModifyOptions.options().returnNew(true), Insumo.class);
            if (despues != null) {
                Tipo tipo = diferencia.signum() > 0 ? Tipo.AJUSTE_POSITIVO : Tipo.AJUSTE_NEGATIVO;
                MovimientoInventario m = movimientos.save(new MovimientoInventario(despues, tipo, diferencia.abs(),
                        motivo, usuario, "AJUSTE", despues.getCostoUnitario(), null, null));
                return new Aplicado(despues, m);
            }
        }
        throw new ReglaNegocioException("Las existencias cambiaron varias veces durante el ajuste. Intente de nuevo");
    }

    /** Costo promedio ponderado: (saldo previo × costo previo + entrada × costo entrada) / saldo nuevo. */
    private void actualizarCostoPromedio(Insumo despues, BigDecimal entrada, BigDecimal costoEntrada) {
        BigDecimal saldoNuevo = despues.getCantidadDisponible();
        BigDecimal saldoPrevio = saldoNuevo.subtract(entrada);
        BigDecimal costoPrevio = despues.getCostoUnitario() == null ? BigDecimal.ZERO : despues.getCostoUnitario();
        BigDecimal nuevoCosto = saldoPrevio.signum() <= 0 || costoPrevio.signum() == 0
                ? costoEntrada
                : saldoPrevio.multiply(costoPrevio).add(entrada.multiply(costoEntrada))
                        .divide(saldoNuevo, 4, RoundingMode.HALF_UP);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(despues.getId())),
                new Update().set("costoUnitario", nuevoCosto), Insumo.class);
        despues.setCostoUnitario(nuevoCosto);
    }

    private static void validarCantidad(BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser positiva");
        }
    }

    private RuntimeException noEncontradoOInactivo(String insumoId) {
        return insumos.existsById(insumoId)
                ? new ReglaNegocioException("El insumo está inactivo y no admite movimientos")
                : new RecursoNoEncontradoException("Insumo", insumoId);
    }
}
