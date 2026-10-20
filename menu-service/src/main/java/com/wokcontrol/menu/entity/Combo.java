package com.wokcontrol.menu.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Combo: varios platos a un precio conjunto. Solo está disponible si todos sus platos lo están. */
@Document("combos")
public class Combo {

    @Id
    private String id;

    @Indexed(unique = true)
    private String nombre;
    private String descripcion;
    private List<ItemCombo> items = new ArrayList<>();
    private BigDecimal precio;
    private boolean disponible = true;
    private boolean activo = true;
    private List<Plato.CambioPrecio> historialPrecios = new ArrayList<>();
    private Imagen imagen;

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public record ItemCombo(String platoId, String nombrePlato, int cantidad) {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public List<ItemCombo> getItems() { return items; }
    public void setItems(List<ItemCombo> items) { this.items = items; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<Plato.CambioPrecio> getHistorialPrecios() { return historialPrecios; }
    public void setHistorialPrecios(List<Plato.CambioPrecio> historialPrecios) { this.historialPrecios = historialPrecios; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Imagen getImagen() { return imagen; }
    public void setImagen(Imagen imagen) { this.imagen = imagen; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
