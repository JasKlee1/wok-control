package com.wokcontrol.pedidos.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * v2 · HU-036 (ADR-002): sesión de mesa que abre el cliente al escanear el QR. No tiene usuario ni
 * contraseña. Vive hasta que el cajero cobra la cuenta de la mesa (o la mesa se libera): desde ese
 * momento el token del celular deja de servir aunque no haya expirado.
 */
@Document("sesiones_mesa")
@CompoundIndex(name = "mesa_estado", def = "{'mesaId': 1, 'estado': 1}")
public class SesionMesa {

    public enum Estado { ACTIVA, CERRADA }

    /**
     * v2 · HU-032: plato del pedido que el cliente arma antes de enviarlo. El precio y el nombre son
     * los de la carta al agregarlo; al enviar se vuelven a tomar de menu-service.
     */
    public static class LineaCarrito {
        private String id = UUID.randomUUID().toString();
        private String tipo;
        private String productoId;
        private String nombre;
        private BigDecimal precioUnitario;
        private int cantidad;
        private int nivelPicante;
        private String notas;

        public BigDecimal getSubtotal() {
            return precioUnitario == null ? BigDecimal.ZERO : precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
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
    }

    @Id
    private String id;
    private String mesaId;
    private int mesaNumero;
    private Estado estado = Estado.ACTIVA;
    private Instant abiertaEn;
    private Instant cerradaEn;
    private String motivoCierre;
    /** v2 · HU-032: pedido sin enviar de esta sesión. */
    private List<LineaCarrito> carrito = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMesaId() { return mesaId; }
    public void setMesaId(String mesaId) { this.mesaId = mesaId; }
    public int getMesaNumero() { return mesaNumero; }
    public void setMesaNumero(int mesaNumero) { this.mesaNumero = mesaNumero; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getAbiertaEn() { return abiertaEn; }
    public void setAbiertaEn(Instant abiertaEn) { this.abiertaEn = abiertaEn; }
    public Instant getCerradaEn() { return cerradaEn; }
    public void setCerradaEn(Instant cerradaEn) { this.cerradaEn = cerradaEn; }
    public String getMotivoCierre() { return motivoCierre; }
    public void setMotivoCierre(String motivoCierre) { this.motivoCierre = motivoCierre; }
    public List<LineaCarrito> getCarrito() { return carrito; }
    public void setCarrito(List<LineaCarrito> carrito) { this.carrito = carrito == null ? new ArrayList<>() : carrito; }
}
