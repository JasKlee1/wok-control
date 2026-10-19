package com.wokcontrol.inventario.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Reporte de insumo agotado enviado desde cocina; el responsable de inventario lo atiende. */
@Document("reportes_agotado")
public class ReporteAgotado {

    @Id
    private String id;
    private String insumoId;
    private String nombreInsumo;
    private String observacion;
    private String reportadoPor;
    private Instant fecha;
    @Indexed
    private boolean atendido;
    private String atendidoPor;
    private Instant atendidoEn;
    private String respuesta;

    public ReporteAgotado() {
    }

    public ReporteAgotado(String insumoId, String nombreInsumo, String observacion, String reportadoPor) {
        this.insumoId = insumoId;
        this.nombreInsumo = nombreInsumo;
        this.observacion = observacion;
        this.reportadoPor = reportadoPor;
        this.fecha = Instant.now();
    }

    public void atender(String usuario, String respuesta) {
        this.atendido = true;
        this.atendidoPor = usuario;
        this.atendidoEn = Instant.now();
        this.respuesta = respuesta;
    }

    public String getId() { return id; }
    public String getInsumoId() { return insumoId; }
    public String getNombreInsumo() { return nombreInsumo; }
    public String getObservacion() { return observacion; }
    public String getReportadoPor() { return reportadoPor; }
    public Instant getFecha() { return fecha; }
    public boolean isAtendido() { return atendido; }
    public String getAtendidoPor() { return atendidoPor; }
    public Instant getAtendidoEn() { return atendidoEn; }
    public String getRespuesta() { return respuesta; }
}
