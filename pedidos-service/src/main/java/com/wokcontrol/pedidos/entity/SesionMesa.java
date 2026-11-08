package com.wokcontrol.pedidos.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * v2 · HU-036 (ADR-002): sesión de mesa que abre el cliente al escanear el QR. No tiene usuario ni
 * contraseña. Vive hasta que el cajero cobra la cuenta de la mesa (o la mesa se libera): desde ese
 * momento el token del celular deja de servir aunque no haya expirado.
 */
@Document("sesiones_mesa")
@CompoundIndex(name = "mesa_estado", def = "{'mesaId': 1, 'estado': 1}")
public class SesionMesa {

    public enum Estado { ACTIVA, CERRADA }

    @Id
    private String id;
    private String mesaId;
    private int mesaNumero;
    private Estado estado = Estado.ACTIVA;
    private Instant abiertaEn;
    private Instant cerradaEn;
    private String motivoCierre;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMesaId() { return mesaId; }
    public void setMesaId(String mesaId) { this.mesaId = mesaId; }
    public int getMesaNumero() { return mesaNumero; }
    public void setMesaNumero(int mesaNumero) { this.mesaNumero = mesaNumero; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getAbiertaEn() { return abiertaEn; }
    public void setAbiertaEn(Instant abiertaEn) { this.abiertaEn = abiertaEn; }
    public Instant getCerradaEn() { return cerradaEn; }
    public void setCerradaEn(Instant cerradaEn) { this.cerradaEn = cerradaEn; }
    public String getMotivoCierre() { return motivoCierre; }
    public void setMotivoCierre(String motivoCierre) { this.motivoCierre = motivoCierre; }
}
