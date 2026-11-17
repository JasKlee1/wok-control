package com.wokcontrol.pedidos.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * v2 · HU-040: grupo que llega a la puerta. Mientras espera mesa está EN_ESPERA; sale de la lista
 * cuando el anfitrión lo sienta (SENTADO) o lo retira (RETIRADO). No se borra, para la auditoría.
 */
@Document("lista_espera")
@CompoundIndex(name = "estado_llegada", def = "{'estado': 1, 'llegadaEn': 1}")
public class GrupoEspera {

    public enum Estado { EN_ESPERA, SENTADO, RETIRADO }

    @Id
    private String id;
    private String nombre;
    private int personas;
    private Instant llegadaEn;
    private String notas;
    private Estado estado = Estado.EN_ESPERA;
    private List<String> mesaIds = new ArrayList<>();
    private List<Integer> mesaNumeros = new ArrayList<>();
    private String mesero;
    private String meseroNombre;
    private Instant sentadoEn;
    private Instant retiradoEn;
    private String motivoRetiro;
    private String registradoPor;

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    /** Minutos que lleva esperando (o que esperó, si ya se sentó). */
    public long minutosEspera(Instant ahora) {
        Instant fin = sentadoEn != null ? sentadoEn : (retiradoEn != null ? retiradoEn : ahora);
        return llegadaEn == null ? 0 : Math.max(0, Duration.between(llegadaEn, fin).toMinutes());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getPersonas() { return personas; }
    public void setPersonas(int personas) { this.personas = personas; }
    public Instant getLlegadaEn() { return llegadaEn; }
    public void setLlegadaEn(Instant llegadaEn) { this.llegadaEn = llegadaEn; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public List<String> getMesaIds() { return mesaIds; }
    public void setMesaIds(List<String> mesaIds) { this.mesaIds = mesaIds; }
    public List<Integer> getMesaNumeros() { return mesaNumeros; }
    public void setMesaNumeros(List<Integer> mesaNumeros) { this.mesaNumeros = mesaNumeros; }
    public String getMesero() { return mesero; }
    public void setMesero(String mesero) { this.mesero = mesero; }
    public String getMeseroNombre() { return meseroNombre; }
    public void setMeseroNombre(String meseroNombre) { this.meseroNombre = meseroNombre; }
    public Instant getSentadoEn() { return sentadoEn; }
    public void setSentadoEn(Instant sentadoEn) { this.sentadoEn = sentadoEn; }
    public Instant getRetiradoEn() { return retiradoEn; }
    public void setRetiradoEn(Instant retiradoEn) { this.retiradoEn = retiradoEn; }
    public String getMotivoRetiro() { return motivoRetiro; }
    public void setMotivoRetiro(String motivoRetiro) { this.motivoRetiro = motivoRetiro; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
