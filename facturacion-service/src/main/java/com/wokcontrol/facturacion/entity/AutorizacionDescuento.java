package com.wokcontrol.facturacion.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Autorización de descuento emitida por un usuario con permiso DESCUENTOS_AUTORIZAR. El cajero la
 * aplica con su código; es de un solo uso, vence y limita el porcentaje máximo.
 */
@Document("autorizaciones_descuento")
public class AutorizacionDescuento {

    @Id
    private String id;
    @Indexed(unique = true)
    private String codigo;
    private BigDecimal porcentajeMaximo;
    private String motivo;
    /** Opcional: si se indica, solo sirve para la cuenta de esa mesa. */
    private String mesaId;
    private Integer mesaNumero;
    private String autorizadoPor;
    private Instant creadaEn;
    private Instant expiraEn;
    private boolean usada;
    private Instant usadaEn;
    private String facturaId;
    private String usadaPor;

    public boolean vigente(Instant ahora) {
        return !usada && expiraEn.isAfter(ahora);
    }

    public boolean isVigenteAhora() {
        return vigente(Instant.now());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public BigDecimal getPorcentajeMaximo() { return porcentajeMaximo; }
    public void setPorcentajeMaximo(BigDecimal p) { this.porcentajeMaximo = p; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getMesaId() { return mesaId; }
    public void setMesaId(String mesaId) { this.mesaId = mesaId; }
    public Integer getMesaNumero() { return mesaNumero; }
    public void setMesaNumero(Integer mesaNumero) { this.mesaNumero = mesaNumero; }
    public String getAutorizadoPor() { return autorizadoPor; }
    public void setAutorizadoPor(String autorizadoPor) { this.autorizadoPor = autorizadoPor; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
    public boolean isUsada() { return usada; }
    public void setUsada(boolean usada) { this.usada = usada; }
    public Instant getUsadaEn() { return usadaEn; }
    public void setUsadaEn(Instant usadaEn) { this.usadaEn = usadaEn; }
    public String getFacturaId() { return facturaId; }
    public void setFacturaId(String facturaId) { this.facturaId = facturaId; }
    public String getUsadaPor() { return usadaPor; }
    public void setUsadaPor(String usadaPor) { this.usadaPor = usadaPor; }
}
