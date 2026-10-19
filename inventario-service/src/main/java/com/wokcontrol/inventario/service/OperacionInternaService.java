package com.wokcontrol.inventario.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.inventario.dto.InventarioDtos.ConsumoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.LineaConsumo;
import com.wokcontrol.inventario.dto.InventarioDtos.LineaRecepcion;
import com.wokcontrol.inventario.dto.InventarioDtos.RecepcionRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.ResultadoOperacion;
import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.MovimientoInventario.Tipo;
import com.wokcontrol.inventario.entity.OperacionIdempotente;
import com.wokcontrol.inventario.entity.OperacionIdempotente.Estado;
import com.wokcontrol.inventario.entity.OperacionIdempotente.Linea;
import com.wokcontrol.inventario.repository.OperacionRepository;

/**
 * Operaciones originadas en otros microservicios, todas idempotentes por referencia:
 * <ul>
 * <li><b>Consumo</b> (pedidos-service, al enviar un pedido a cocina): descuenta los insumos de la
 * receta. Si un insumo no alcanza, se compensan los descuentos ya hechos y no se registra nada.</li>
 * <li><b>Reversión</b> (pedido cancelado antes de prepararse): devuelve lo consumido una sola vez.</li>
 * <li><b>Recepción</b> (proveedores-service, al confirmar la mercancía de una orden de compra).</li>
 * </ul>
 * No hay transacciones distribuidas: la referencia única en "operaciones_inventario" garantiza que un
 * reintento no descuente ni sume dos veces.
 */
@Service
public class OperacionInternaService {

    private static final Logger log = LoggerFactory.getLogger(OperacionInternaService.class);

    private final OperacionRepository operaciones;
    private final ExistenciasService existencias;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;

    public OperacionInternaService(OperacionRepository operaciones, ExistenciasService existencias, MongoTemplate mongo,
            AuditoriaPublisher auditoria) {
        this.operaciones = operaciones;
        this.existencias = existencias;
        this.mongo = mongo;
        this.auditoria = auditoria;
    }

    public ResultadoOperacion consumir(ConsumoRequest req) {
        String usuario = UsuarioActual.responsableActual();
        OperacionIdempotente existente = reservar(req.referencia(), OperacionIdempotente.Tipo.CONSUMO, usuario);
        if (existente != null) {
            return yaProcesada(existente);
        }
        Map<String, BigDecimal> agregadas = new LinkedHashMap<>();
        for (LineaConsumo l : req.lineas()) {
            agregadas.merge(l.insumoId(), l.cantidad(), BigDecimal::add);
        }
        List<Linea> aplicadas = new ArrayList<>();
        try {
            for (Map.Entry<String, BigDecimal> e : agregadas.entrySet()) {
                Insumo despues = existencias.disminuir(e.getKey(), e.getValue(), Tipo.CONSUMO,
                        req.descripcion() == null ? "Consumo por pedido" : req.descripcion(), usuario, req.referencia()).despues();
                aplicadas.add(new Linea(e.getKey(), despues.getNombre(), e.getValue()));
            }
        } catch (RuntimeException ex) {
            compensar(req.referencia(), aplicadas, usuario);
            operaciones.deleteById(req.referencia());
            throw ex;
        }
        finalizar(req.referencia(), aplicadas, Estado.APLICADO);
        auditoria.registrar("CONSUMO", "Inventario", req.referencia(), aplicadas.size() + " insumos descontados");
        return new ResultadoOperacion(req.referencia(), Estado.APLICADO.name(), false, aplicadas.size());
    }

    public ResultadoOperacion revertirConsumo(String referencia) {
        String usuario = UsuarioActual.responsableActual();
        OperacionIdempotente op = operaciones.findById(referencia).orElse(null);
        if (op == null) {
            // nunca se consumió (p. ej., pedido sin receta): no hay nada que devolver
            return new ResultadoOperacion(referencia, "SIN_CONSUMO", true, 0);
        }
        if (op.getEstado() == Estado.REVERTIDO) {
            return yaProcesada(op);
        }
        if (op.getEstado() == Estado.EN_PROCESO) {
            throw new ConflictoException("El consumo " + referencia + " todavía se está procesando; intente de nuevo");
        }
        // marca la reversión antes de devolver existencias: un segundo intento concurrente no la repite
        OperacionIdempotente marcada = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(referencia).and("estado").is(Estado.APLICADO)),
                new Update().set("estado", Estado.REVERTIDO).set("actualizadoEn", java.time.Instant.now()),
                OperacionIdempotente.class);
        if (marcada == null) {
            return yaProcesada(operaciones.findById(referencia).orElse(op));
        }
        for (Linea l : op.getLineas()) {
            existencias.aumentar(l.insumoId(), l.cantidad(), Tipo.REVERSION_CONSUMO, "Pedido cancelado antes de prepararse",
                    usuario, referencia, null, null, null);
        }
        auditoria.registrar("REVERSION_CONSUMO", "Inventario", referencia, op.getLineas().size() + " insumos devueltos");
        return new ResultadoOperacion(referencia, Estado.REVERTIDO.name(), false, op.getLineas().size());
    }

    public ResultadoOperacion recibir(RecepcionRequest req) {
        String usuario = UsuarioActual.responsableActual();
        OperacionIdempotente existente = reservar(req.referencia(), OperacionIdempotente.Tipo.RECEPCION, usuario);
        if (existente != null) {
            return yaProcesada(existente);
        }
        List<Linea> aplicadas = new ArrayList<>();
        List<LineaRecepcion> pendientesCompensar = new ArrayList<>();
        try {
            for (LineaRecepcion l : req.lineas()) {
                Insumo despues = existencias.aumentar(l.insumoId(), l.cantidad(), Tipo.RECEPCION_COMPRA,
                        "Recepción de orden de compra", usuario, req.referencia(), l.costoUnitario(), l.lote(),
                        l.fechaVencimiento()).despues();
                pendientesCompensar.add(l);
                if (l.lote() != null && !l.lote().isBlank()) {
                    mongo.updateFirst(Query.query(Criteria.where("_id").is(l.insumoId())),
                            new Update().push("lotes", new Insumo.Lote(l.lote().trim(), l.cantidad(), l.fechaVencimiento(),
                                    java.time.Instant.now(), req.referencia(), false)), Insumo.class);
                }
                aplicadas.add(new Linea(l.insumoId(), despues.getNombre(), l.cantidad()));
            }
        } catch (RuntimeException ex) {
            for (LineaRecepcion l : pendientesCompensar) {
                try {
                    existencias.disminuir(l.insumoId(), l.cantidad(), Tipo.SALIDA, "Compensación: recepción fallida",
                            usuario, req.referencia());
                } catch (RuntimeException ce) {
                    log.error("No se pudo compensar la recepción {} del insumo {}: {}", req.referencia(), l.insumoId(), ce.getMessage());
                }
            }
            operaciones.deleteById(req.referencia());
            throw ex;
        }
        finalizar(req.referencia(), aplicadas, Estado.APLICADO);
        auditoria.registrar("RECEPCION_COMPRA", "Inventario", req.referencia(),
                aplicadas.size() + " insumos ingresados del proveedor " + req.proveedorId());
        return new ResultadoOperacion(req.referencia(), Estado.APLICADO.name(), false, aplicadas.size());
    }

    /** Inserta el registro de idempotencia; si ya existe devuelve el registro previo. */
    private OperacionIdempotente reservar(String referencia, OperacionIdempotente.Tipo tipo, String usuario) {
        try {
            mongo.insert(new OperacionIdempotente(referencia, tipo, usuario));
            return null;
        } catch (DuplicateKeyException ex) {
            OperacionIdempotente previa = operaciones.findById(referencia)
                    .orElseThrow(() -> new ConflictoException("Operación " + referencia + " en conflicto; reintente"));
            if (previa.getTipo() != tipo) {
                throw new ConflictoException("La referencia " + referencia + " ya se usó para otra operación");
            }
            if (previa.getEstado() == Estado.EN_PROCESO) {
                throw new ConflictoException("La operación " + referencia + " se está procesando; intente de nuevo en unos segundos");
            }
            return previa;
        }
    }

    private void finalizar(String referencia, List<Linea> lineas, Estado estado) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(referencia)),
                new Update().set("estado", estado).set("lineas", lineas).set("actualizadoEn", java.time.Instant.now()),
                OperacionIdempotente.class);
    }

    private void compensar(String referencia, List<Linea> aplicadas, String usuario) {
        for (Linea l : aplicadas) {
            try {
                existencias.aumentar(l.insumoId(), l.cantidad(), Tipo.REVERSION_CONSUMO,
                        "Compensación: el consumo no pudo completarse", usuario, referencia, null, null, null);
            } catch (RuntimeException ex) {
                log.error("No se pudo compensar el consumo {} del insumo {}: {}", referencia, l.insumoId(), ex.getMessage());
            }
        }
    }

    private static ResultadoOperacion yaProcesada(OperacionIdempotente op) {
        return new ResultadoOperacion(op.getReferencia(), op.getEstado().name(), true, op.getLineas().size());
    }
}
