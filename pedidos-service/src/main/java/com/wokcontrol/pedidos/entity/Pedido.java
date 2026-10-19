package com.wokcontrol.pedidos.entity;

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
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("pedidos")
@CompoundIndexes({
        @CompoundIndex(name = "mesa_estado", def = "{'mesaId': 1, 'estado': 1}"),
        @CompoundIndex(name = "estado_enviado", def = "{'estado': 1, 'enviadoEn': 1}") })
public class Pedido {

    public enum EstadoConsumo { NO_REQUERIDO, APLICADO, REVERTIDO }

    public record CambioEstado(Instant fecha, String estado, String usuario, String detalle) {
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String numero;
    private String mesaId;
    private int mesaNumero;
    private String mesero;
    private String meseroNombre;
    @Indexed
    private EstadoPedido estado = EstadoPedido.ABIERTO;
    private List<ItemPedido> items = new ArrayList<>();
    private String observaciones;
    /** Alergias declaradas por los comensales (MANI, MARISCOS, ...). */
    private Set<String> alergias = new LinkedHashSet<>();
    private EstadoConsumo consumoInventario = EstadoConsumo.NO_REQUERIDO;
    private String facturaId;
    private String canceladoPor;
    private String motivoCancelacion;
    private List<CambioEstado> historial = new ArrayList<>();

    @CreatedDate
    @Indexed
    private Instant creadoEn;
    private Instant enviadoEn;
    private Instant listoEn;
    private Instant entregadoEn;
    private Instant pagadoEn;
    private Instant canceladoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public BigDecimal getTotal() {
        return items.stream().map(ItemPedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getUnidades() {
        return items.stream().mapToInt(ItemPedido::getCantidad).sum();
    }

    public String referenciaInventario() {
        return "PEDIDO-" + id;
    }

    public void registrar(String estadoTexto, String usuario, String detalle) {
        historial.add(new CambioEstado(Instant.now(), estadoTexto, usuario, detalle));
    }

    /**
     * Estado derivado del avance de cocina: todos entregados → ENTREGADO; todos listos o entregados
     * → LISTO; alguno en preparación o listo → EN_PREPARACION; si no, sigue ENVIADO.
     */
    public void recalcularEstado() {
        if (!EstadoPedido.EN_COCINA.contains(estado) && estado != EstadoPedido.ENTREGADO) {
            return;
        }
        boolean todosEntregados = items.stream().allMatch(i -> i.getEstado() == ItemPedido.Estado.ENTREGADO);
        boolean todosListos = items.stream().allMatch(i -> i.getEstado().ordinal() >= ItemPedido.Estado.LISTO.ordinal());
        boolean algunoAvanzado = items.stream().anyMatch(i -> i.getEstado() != ItemPedido.Estado.PENDIENTE);
        EstadoPedido nuevo = todosEntregados ? EstadoPedido.ENTREGADO
                : todosListos ? EstadoPedido.LISTO
                : algunoAvanzado ? EstadoPedido.EN_PREPARACION
                : EstadoPedido.ENVIADO;
        if (nuevo != estado) {
            estado = nuevo;
            if (nuevo == EstadoPedido.LISTO && listoEn == null) {
                listoEn = Instant.now();
            }
            if (nuevo == EstadoPedido.ENTREGADO) {
                entregadoEn = Instant.now();
            }
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getMesaId() { return mesaId; }
    public void setMesaId(String mesaId) { this.mesaId = mesaId; }
    public int getMesaNumero() { return mesaNumero; }
    public void setMesaNumero(int mesaNumero) { this.mesaNumero = mesaNumero; }
    public String getMesero() { return mesero; }
    public void setMesero(String mesero) { this.mesero = mesero; }
    public String getMeseroNombre() { return meseroNombre; }
    public void setMeseroNombre(String meseroNombre) { this.meseroNombre = meseroNombre; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public List<ItemPedido> getItems() { return items; }
    public void setItems(List<ItemPedido> items) { this.items = items; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public Set<String> getAlergias() { return alergias; }
    public void setAlergias(Set<String> alergias) { this.alergias = alergias; }
    public EstadoConsumo getConsumoInventario() { return consumoInventario; }
    public void setConsumoInventario(EstadoConsumo consumoInventario) { this.consumoInventario = consumoInventario; }
    public String getFacturaId() { return facturaId; }
    public void setFacturaId(String facturaId) { this.facturaId = facturaId; }
    public String getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(String canceladoPor) { this.canceladoPor = canceladoPor; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
    public List<CambioEstado> getHistorial() { return historial; }
    public void setHistorial(List<CambioEstado> historial) { this.historial = historial; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
    public Instant getEnviadoEn() { return enviadoEn; }
    public void setEnviadoEn(Instant enviadoEn) { this.enviadoEn = enviadoEn; }
    public Instant getListoEn() { return listoEn; }
    public void setListoEn(Instant listoEn) { this.listoEn = listoEn; }
    public Instant getEntregadoEn() { return entregadoEn; }
    public void setEntregadoEn(Instant entregadoEn) { this.entregadoEn = entregadoEn; }
    public Instant getPagadoEn() { return pagadoEn; }
    public void setPagadoEn(Instant pagadoEn) { this.pagadoEn = pagadoEn; }
    public Instant getCanceladoEn() { return canceladoEn; }
    public void setCanceladoEn(Instant canceladoEn) { this.canceladoEn = canceladoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
