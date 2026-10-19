package com.wokcontrol.inventario.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Control de idempotencia de operaciones originadas en otros servicios. El _id es la referencia de
 * negocio (PEDIDO-xxx, OC-xxx): si una llamada se reintenta, la inserción falla por clave duplicada y
 * la operación no se aplica dos veces.
 */
@Document("operaciones_inventario")
public class OperacionIdempotente {

    public enum Tipo { CONSUMO, RECEPCION }

    public enum Estado { EN_PROCESO, APLICADO, REVERTIDO }

    public record Linea(String insumoId, String nombreInsumo, BigDecimal cantidad) {
    }

    @Id
    private String referencia;
    private Tipo tipo;
    private Estado estado;
    private List<Linea> lineas;
    private String usuario;
    private Instant creadoEn;
    private Instant actualizadoEn;

    public OperacionIdempotente() {
    }

    public OperacionIdempotente(String referencia, Tipo tipo, String usuario) {
        this.referencia = referencia;
        this.tipo = tipo;
        this.estado = Estado.EN_PROCESO;
        this.usuario = usuario;
        this.creadoEn = Instant.now();
        this.actualizadoEn = creadoEn;
        this.lineas = List.of();
    }

    public String getReferencia() { return referencia; }
    public Tipo getTipo() { return tipo; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; this.actualizadoEn = Instant.now(); }
    public List<Linea> getLineas() { return lineas; }
    public void setLineas(List<Linea> lineas) { this.lineas = lineas; }
    public String getUsuario() { return usuario; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
