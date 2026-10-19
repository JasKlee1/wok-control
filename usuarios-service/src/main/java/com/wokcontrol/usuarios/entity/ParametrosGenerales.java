package com.wokcontrol.usuarios.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Parámetros generales de la plataforma (documento único con id "general").
 */
@Document("parametros")
public class ParametrosGenerales {

    public static final String ID = "general";

    @Id
    private String id = ID;
    private String nombreRestaurante = "Wok Control";
    private String nit;
    private String direccion;
    private String telefono;
    private BigDecimal impuestoConsumoPorcentaje = new BigDecimal("8");
    private String mensajeComprobante = "Gracias por su visita. Este documento es un comprobante interno de venta.";
    private int minutosAlertaCocina = 20;
    private String actualizadoPor;
    private Instant actualizadoEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombreRestaurante() { return nombreRestaurante; }
    public void setNombreRestaurante(String nombreRestaurante) { this.nombreRestaurante = nombreRestaurante; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public BigDecimal getImpuestoConsumoPorcentaje() { return impuestoConsumoPorcentaje; }
    public void setImpuestoConsumoPorcentaje(BigDecimal v) { this.impuestoConsumoPorcentaje = v; }
    public String getMensajeComprobante() { return mensajeComprobante; }
    public void setMensajeComprobante(String mensajeComprobante) { this.mensajeComprobante = mensajeComprobante; }
    public int getMinutosAlertaCocina() { return minutosAlertaCocina; }
    public void setMinutosAlertaCocina(int minutosAlertaCocina) { this.minutosAlertaCocina = minutosAlertaCocina; }
    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
