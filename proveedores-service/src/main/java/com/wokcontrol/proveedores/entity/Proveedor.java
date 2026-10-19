package com.wokcontrol.proveedores.entity;

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

@Document("proveedores")
public class Proveedor {

    public record Contacto(String nombre, String cargo, String telefono, String email) {
    }

    public record CondicionesComerciales(Integer plazoPagoDias, BigDecimal pedidoMinimo, Integer tiempoEntregaDias,
            String formaPago, String observaciones) {
    }

    /** Insumo que el proveedor suministra (id de inventario-service y nombre como copia). */
    public record InsumoSuministrado(String insumoId, String nombre, String unidad) {
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String nit;
    private String razonSocial;
    @Indexed
    private String nombreComercial;
    private String telefono;
    private String email;
    private String direccion;
    private String ciudad;
    private Contacto contacto;
    private Set<String> categorias = new LinkedHashSet<>();
    private List<InsumoSuministrado> insumos = new ArrayList<>();
    private CondicionesComerciales condiciones;
    private boolean activo = true;

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public Contacto getContacto() { return contacto; }
    public void setContacto(Contacto contacto) { this.contacto = contacto; }
    public Set<String> getCategorias() { return categorias; }
    public void setCategorias(Set<String> categorias) { this.categorias = categorias; }
    public List<InsumoSuministrado> getInsumos() { return insumos; }
    public void setInsumos(List<InsumoSuministrado> insumos) { this.insumos = insumos; }
    public CondicionesComerciales getCondiciones() { return condiciones; }
    public void setCondiciones(CondicionesComerciales condiciones) { this.condiciones = condiciones; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
