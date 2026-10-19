package com.wokcontrol.usuarios.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de auditoría central (solo inserción, nunca se modifica ni elimina desde la aplicación).
 */
@Document("auditoria")
@CompoundIndex(name = "usuario_fecha", def = "{'usuario': 1, 'fecha': -1}")
public class RegistroAuditoria {

    @Id
    private String id;
    private String servicio;
    private String usuario;
    private String accion;
    private String entidad;
    private String entidadId;
    private String detalle;

    @Indexed
    private Instant fecha;

    public RegistroAuditoria() {
    }

    public RegistroAuditoria(String servicio, String usuario, String accion, String entidad, String entidadId,
            String detalle, Instant fecha) {
        this.servicio = servicio;
        this.usuario = usuario;
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.detalle = detalle;
        this.fecha = fecha;
    }

    public String getId() { return id; }
    public String getServicio() { return servicio; }
    public String getUsuario() { return usuario; }
    public String getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public String getEntidadId() { return entidadId; }
    public String getDetalle() { return detalle; }
    public Instant getFecha() { return fecha; }
}
