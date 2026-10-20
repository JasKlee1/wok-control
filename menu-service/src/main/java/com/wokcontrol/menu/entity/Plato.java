package com.wokcontrol.menu.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Plato de la carta. La receta relaciona el plato con insumos de inventario-service por
 * identificador; el nombre y la unidad se guardan como copia para mostrar sin consultar el otro
 * servicio. Los cambios de precio quedan en el historial.
 */
@Document("platos")
public class Plato {

    @Id
    private String id;

    @Indexed(unique = true)
    private String nombre;
    private String descripcion;

    @Indexed
    private String categoriaId;
    private BigDecimal precio;
    private boolean disponible = true;
    private boolean activo = true;
    /** Nivel de picante base del plato: 0 (no pica) a 3 (muy picante). */
    private int picanteBase;
    private Set<String> alergenos = new LinkedHashSet<>();
    private Integer tiempoPreparacionMin;
    private List<IngredienteReceta> receta = new ArrayList<>();
    private List<CambioPrecio> historialPrecios = new ArrayList<>();
    private Imagen imagen;

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public record IngredienteReceta(String insumoId, String nombreInsumo, String unidad, BigDecimal cantidad) {
    }

    public record CambioPrecio(Instant fecha, BigDecimal anterior, BigDecimal nuevo, String usuario) {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCategoriaId() { return categoriaId; }
    public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public int getPicanteBase() { return picanteBase; }
    public void setPicanteBase(int picanteBase) { this.picanteBase = picanteBase; }
    public Set<String> getAlergenos() { return alergenos; }
    public void setAlergenos(Set<String> alergenos) { this.alergenos = alergenos; }
    public Integer getTiempoPreparacionMin() { return tiempoPreparacionMin; }
    public void setTiempoPreparacionMin(Integer tiempoPreparacionMin) { this.tiempoPreparacionMin = tiempoPreparacionMin; }
    public List<IngredienteReceta> getReceta() { return receta; }
    public void setReceta(List<IngredienteReceta> receta) { this.receta = receta; }
    public List<CambioPrecio> getHistorialPrecios() { return historialPrecios; }
    public void setHistorialPrecios(List<CambioPrecio> historialPrecios) { this.historialPrecios = historialPrecios; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Imagen getImagen() { return imagen; }
    public void setImagen(Imagen imagen) { this.imagen = imagen; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
