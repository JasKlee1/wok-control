package com.wokcontrol.pedidos.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("mesas")
public class Mesa {

    public enum Estado { DISPONIBLE, OCUPADA, FUERA_DE_SERVICIO }

    @Id
    private String id;

    @Indexed(unique = true)
    private int numero;
    private int capacidad;
    private String zona;
    private Estado estado = Estado.DISPONIBLE;
    private boolean activa = true;
    private Instant ocupadaDesde;

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public int getCapacidad() { return capacidad; }
    public void setCapacidad(int capacidad) { this.capacidad = capacidad; }
    public String getZona() { return zona; }
    public void setZona(String zona) { this.zona = zona; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public Instant getOcupadaDesde() { return ocupadaDesde; }
    public void setOcupadaDesde(Instant ocupadaDesde) { this.ocupadaDesde = ocupadaDesde; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
