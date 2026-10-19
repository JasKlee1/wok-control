package com.wokcontrol.pedidos.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Producto solicitado. Precio, nombre, alérgenos y receta son una copia tomada de menu-service al
 * registrar el pedido: los cambios posteriores en la carta no alteran pedidos existentes.
 */
public class ItemPedido {

    public enum Tipo { PLATO, COMBO }

    public enum Estado {
        PENDIENTE, EN_PREPARACION, LISTO, ENTREGADO;

        public boolean puedePasarA(Estado destino) {
            return destino.ordinal() == this.ordinal() + 1;
        }

        public String etiqueta() {
            return switch (this) {
                case PENDIENTE -> "Pendiente";
                case EN_PREPARACION -> "Preparando";
                case LISTO -> "Listo";
                case ENTREGADO -> "Entregado";
            };
        }
    }

    public record ConsumoInsumo(String insumoId, String nombreInsumo, String unidad, BigDecimal cantidadPorUnidad) {
    }

    private String id = UUID.randomUUID().toString();
    private Tipo tipo;
    private String productoId;
    private String nombre;
    private BigDecimal precioUnitario;
    private int cantidad;
    /** 0 a 3; solicitado por el cliente para este producto. */
    private int nivelPicante;
    private String notas;
    private Set<String> alergenos = new LinkedHashSet<>();
    private List<ConsumoInsumo> receta = new ArrayList<>();
    private Estado estado = Estado.PENDIENTE;
    private Instant actualizadoEn;

    public BigDecimal getSubtotal() {
        return precioUnitario == null ? BigDecimal.ZERO : precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public int getNivelPicante() { return nivelPicante; }
    public void setNivelPicante(int nivelPicante) { this.nivelPicante = nivelPicante; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Set<String> getAlergenos() { return alergenos; }
    public void setAlergenos(Set<String> alergenos) { this.alergenos = alergenos; }
    public List<ConsumoInsumo> getReceta() { return receta; }
    public void setReceta(List<ConsumoInsumo> receta) { this.receta = receta; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
