package com.wokcontrol.usuarios.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Rol = conjunto de permisos. Los 7 roles del sistema se sincronizan desde el código al iniciar y
 * no se pueden modificar; los roles personalizados combinan permisos del catálogo.
 */
@Document("roles")
public class Rol {

    /** Código del rol en mayúsculas (ADMINISTRADOR, MESERO, ...). */
    @Id
    private String codigo;
    private String nombre;
    private String descripcion;
    private Set<String> permisos = new LinkedHashSet<>();
    private boolean sistema;
    private boolean activo = true;

    @LastModifiedDate
    private Instant actualizadoEn;

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Set<String> getPermisos() { return permisos; }
    public void setPermisos(Set<String> permisos) { this.permisos = permisos; }
    public boolean isSistema() { return sistema; }
    public void setSistema(boolean sistema) { this.sistema = sistema; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
