package com.wokcontrol.proveedores.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Orden de compra. El inventario solo aumenta cuando el responsable confirma la recepción física
 * (estado RECIBIDA); el reporte de entrega del proveedor no modifica existencias.
 */
@Document("ordenes_compra")
public class OrdenCompra {

    public enum Estado {
        BORRADOR, ENVIADA, CONFIRMADA, ENTREGA_REPORTADA, RECIBIDA, CANCELADA;

        public static final Set<Estado> PENDIENTES = EnumSet.of(ENVIADA, CONFIRMADA, ENTREGA_REPORTADA);

        public boolean permiteRecepcion() {
            return PENDIENTES.contains(this);
        }

        public boolean permiteCancelar() {
            return this == BORRADOR || this == ENVIADA || this == CONFIRMADA;
        }

        public String etiqueta() {
            return switch (this) {
                case BORRADOR -> "Borrador";
                case ENVIADA -> "Enviada al proveedor";
                case CONFIRMADA -> "Confirmada";
                case ENTREGA_REPORTADA -> "Entrega reportada";
                case RECIBIDA -> "Recibida";
                case CANCELADA -> "Cancelada";
            };
        }

        public String claseCss() {
            return switch (this) {
                case BORRADOR -> "neutro";
                case ENVIADA -> "info";
                case CONFIRMADA -> "dorado";
                case ENTREGA_REPORTADA -> "alerta";
                case RECIBIDA -> "exito";
                case CANCELADA -> "error";
            };
        }
    }

    public record Item(String insumoId, String nombreInsumo, String unidad, BigDecimal cantidad, BigDecimal costoUnitario,
            BigDecimal cantidadRecibida, String lote, LocalDate fechaVencimiento) {

        public BigDecimal subtotal() {
            return cantidad.multiply(costoUnitario);
        }
    }

    public record Entrega(Instant fechaReporte, LocalDate fechaEntrega, String documento, String observaciones,
            String reportadaPor) {
    }

    public record Recepcion(Instant fecha, String recibidoPor, String observaciones, boolean sincronizadaInventario,
            String errorSincronizacion) {
    }

    public record CambioEstado(Instant fecha, String estado, String usuario, String detalle) {
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String numero;
    @Indexed
    private String proveedorId;
    private String proveedorNombre;
    @Indexed
    private Estado estado = Estado.BORRADOR;
    private List<Item> items = new ArrayList<>();
    private LocalDate fechaEntregaEsperada;
    private String notas;
    private Entrega entrega;
    private Recepcion recepcion;
    private String creadoPor;
    private String motivoCancelacion;
    private List<CambioEstado> historial = new ArrayList<>();

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public BigDecimal getTotal() {
        return items.stream().map(Item::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalRecibido() {
        return items.stream().filter(i -> i.cantidadRecibida() != null)
                .map(i -> i.cantidadRecibida().multiply(i.costoUnitario())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String referenciaInventario() {
        return "OC-" + id;
    }

    public void registrar(String estadoTexto, String usuario, String detalle) {
        historial.add(new CambioEstado(Instant.now(), estadoTexto, usuario, detalle));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getProveedorId() { return proveedorId; }
    public void setProveedorId(String proveedorId) { this.proveedorId = proveedorId; }
    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String proveedorNombre) { this.proveedorNombre = proveedorNombre; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public LocalDate getFechaEntregaEsperada() { return fechaEntregaEsperada; }
    public void setFechaEntregaEsperada(LocalDate f) { this.fechaEntregaEsperada = f; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Entrega getEntrega() { return entrega; }
    public void setEntrega(Entrega entrega) { this.entrega = entrega; }
    public Recepcion getRecepcion() { return recepcion; }
    public void setRecepcion(Recepcion recepcion) { this.recepcion = recepcion; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
    public List<CambioEstado> getHistorial() { return historial; }
    public void setHistorial(List<CambioEstado> historial) { this.historial = historial; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
