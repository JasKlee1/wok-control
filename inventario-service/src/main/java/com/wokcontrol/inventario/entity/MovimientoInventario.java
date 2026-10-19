package com.wokcontrol.inventario.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro inmutable de cada cambio de existencias: tipo, cantidad, saldo anterior y nuevo, motivo,
 * usuario, fecha y referencia (pedido, orden de compra o ajuste).
 */
@Document("movimientos_inventario")
@CompoundIndexes({ @CompoundIndex(name = "insumo_fecha", def = "{'insumoId': 1, 'fecha': -1}") })
public class MovimientoInventario {

    public enum Tipo {
        ENTRADA(true), SALIDA(false), MERMA(false), AJUSTE_POSITIVO(true), AJUSTE_NEGATIVO(false),
        CONSUMO(false), REVERSION_CONSUMO(true), RECEPCION_COMPRA(true);

        private final boolean suma;

        Tipo(boolean suma) {
            this.suma = suma;
        }

        public boolean suma() {
            return suma;
        }
    }

    @Id
    private String id;
    private String insumoId;
    private String nombreInsumo;
    private String unidad;
    private Tipo tipo;
    private BigDecimal cantidad;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoNuevo;
    private BigDecimal costoUnitario;
    private String motivo;
    private String usuario;
    @Indexed
    private Instant fecha;
    @Indexed
    private String referencia;
    private String lote;
    private LocalDate fechaVencimiento;

    public MovimientoInventario() {
    }

    public MovimientoInventario(Insumo despues, Tipo tipo, BigDecimal cantidad, String motivo, String usuario,
            String referencia, BigDecimal costoUnitario, String lote, LocalDate fechaVencimiento) {
        this.insumoId = despues.getId();
        this.nombreInsumo = despues.getNombre();
        this.unidad = despues.getUnidad();
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.saldoNuevo = despues.getCantidadDisponible();
        this.saldoAnterior = tipo.suma() ? saldoNuevo.subtract(cantidad) : saldoNuevo.add(cantidad);
        this.motivo = motivo;
        this.usuario = usuario;
        this.fecha = Instant.now();
        this.referencia = referencia;
        this.costoUnitario = costoUnitario;
        this.lote = lote;
        this.fechaVencimiento = fechaVencimiento;
    }

    public String getId() { return id; }
    public String getInsumoId() { return insumoId; }
    public String getNombreInsumo() { return nombreInsumo; }
    public String getUnidad() { return unidad; }
    public Tipo getTipo() { return tipo; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getSaldoAnterior() { return saldoAnterior; }
    public BigDecimal getSaldoNuevo() { return saldoNuevo; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public String getMotivo() { return motivo; }
    public String getUsuario() { return usuario; }
    public Instant getFecha() { return fecha; }
    public String getReferencia() { return referencia; }
    public String getLote() { return lote; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
}
