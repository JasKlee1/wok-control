package com.wokcontrol.inventario.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Insumo o materia prima. La cantidad disponible NUNCA se edita directamente: solo cambia mediante
 * movimientos (entradas, salidas, mermas, consumos, recepciones o ajustes auditados) aplicados con
 * operaciones atómicas en MongoDB.
 */
@Document("insumos")
public class Insumo {

    @Id
    private String id;

    @Indexed(unique = true)
    private String nombre;
    private String categoria;
    private String unidad;
    private BigDecimal cantidadDisponible = BigDecimal.ZERO;
    private BigDecimal stockMinimo = BigDecimal.ZERO;
    /** Costo promedio ponderado por unidad. */
    private BigDecimal costoUnitario = BigDecimal.ZERO;
    private boolean perecedero;
    private boolean activo = true;
    private List<Lote> lotes = new ArrayList<>();

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public record Lote(String codigo, BigDecimal cantidadInicial, LocalDate fechaVencimiento, Instant fechaIngreso,
            String referencia, boolean cerrado) {
    }

    public boolean isBajoStock() {
        return activo && cantidadDisponible != null && stockMinimo != null && cantidadDisponible.compareTo(stockMinimo) <= 0;
    }

    public BigDecimal getValorizado() {
        return cantidadDisponible == null || costoUnitario == null ? BigDecimal.ZERO : cantidadDisponible.multiply(costoUnitario);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public BigDecimal getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(BigDecimal cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }
    public boolean isPerecedero() { return perecedero; }
    public void setPerecedero(boolean perecedero) { this.perecedero = perecedero; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<Lote> getLotes() { return lotes; }
    public void setLotes(List<Lote> lotes) { this.lotes = lotes; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
