package com.wokcontrol.domicilios.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * v2 · CR-001: pedido a domicilio tomado por teléfono. Guarda los datos del cliente (Ley 1581: solo los
 * ve el cajero y el domiciliario asignado, y no se escriben en los registros), el pedido de cocina en
 * pedidos-service, el valor a cobrar, el domiciliario, el cobro y el historial de estados (HU-053).
 */
@Document("domicilios")
@CompoundIndexes({
        @CompoundIndex(name = "domiciliario_estado", def = "{'domiciliario': 1, 'estado': 1}"),
        @CompoundIndex(name = "estado_creado", def = "{'estado': 1, 'creadoEn': 1}") })
public class Domicilio {

    public record Cliente(String nombre, String telefono, String direccion, String referencia) {
    }

    public record Linea(String productoId, String nombre, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    /** AC-053.2: quién, cuándo (UTC), estado anterior y nuevo. */
    public record CambioEstado(Instant fecha, String usuario, EstadoDomicilio anterior, EstadoDomicilio nuevo, String detalle) {
    }

    /** HU-050: cobro contra entrega y comprobante emitido por facturacion-service. */
    public record Cobro(String metodo, BigDecimal recibido, BigDecimal cambio, String facturaId, String facturaNumero,
            String referencia, Instant fecha) {
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String numero;
    private Cliente cliente;
    private List<Linea> lineas = new ArrayList<>();
    private String observaciones;
    private BigDecimal subtotalPlatos;
    private BigDecimal impuestoPorcentaje;
    private BigDecimal impuestoValor;
    private BigDecimal valorDomicilio;
    private BigDecimal total;
    private EstadoDomicilio estado;
    @Indexed(unique = true, sparse = true)
    private String pedidoId;
    private String pedidoNumero;
    private String registradoPor;
    private String registradoPorNombre;
    private String domiciliario;
    private String domiciliarioNombre;
    private Instant asignadoEn;
    private Instant enCaminoEn;
    private Instant entregadoEn;
    private Instant cerradoEn;
    private Cobro cobro;
    private String motivoNoEntrega;
    private String motivoCancelacion;
    private List<CambioEstado> historial = new ArrayList<>();
    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    /** Cambia el estado y deja la huella en el historial (AC-053.2). */
    public void cambiarEstado(EstadoDomicilio nuevo, String usuario, String detalle) {
        historial.add(new CambioEstado(Instant.now(), usuario, estado, nuevo, detalle));
        estado = nuevo;
        if (EstadoDomicilio.FINALES.contains(nuevo)) {
            cerradoEn = Instant.now();
        }
    }

    public int getUnidades() {
        return lineas.stream().mapToInt(Linea::cantidad).sum();
    }

    public boolean esDe(String usuario) {
        return domiciliario != null && domiciliario.equals(usuario);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public List<Linea> getLineas() { return lineas; }
    public void setLineas(List<Linea> lineas) { this.lineas = lineas; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public BigDecimal getSubtotalPlatos() { return subtotalPlatos; }
    public void setSubtotalPlatos(BigDecimal subtotalPlatos) { this.subtotalPlatos = subtotalPlatos; }
    public BigDecimal getImpuestoPorcentaje() { return impuestoPorcentaje; }
    public void setImpuestoPorcentaje(BigDecimal impuestoPorcentaje) { this.impuestoPorcentaje = impuestoPorcentaje; }
    public BigDecimal getImpuestoValor() { return impuestoValor; }
    public void setImpuestoValor(BigDecimal impuestoValor) { this.impuestoValor = impuestoValor; }
    public BigDecimal getValorDomicilio() { return valorDomicilio; }
    public void setValorDomicilio(BigDecimal valorDomicilio) { this.valorDomicilio = valorDomicilio; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public EstadoDomicilio getEstado() { return estado; }
    public void setEstado(EstadoDomicilio estado) { this.estado = estado; }
    public String getPedidoId() { return pedidoId; }
    public void setPedidoId(String pedidoId) { this.pedidoId = pedidoId; }
    public String getPedidoNumero() { return pedidoNumero; }
    public void setPedidoNumero(String pedidoNumero) { this.pedidoNumero = pedidoNumero; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public String getRegistradoPorNombre() { return registradoPorNombre; }
    public void setRegistradoPorNombre(String registradoPorNombre) { this.registradoPorNombre = registradoPorNombre; }
    public String getDomiciliario() { return domiciliario; }
    public void setDomiciliario(String domiciliario) { this.domiciliario = domiciliario; }
    public String getDomiciliarioNombre() { return domiciliarioNombre; }
    public void setDomiciliarioNombre(String domiciliarioNombre) { this.domiciliarioNombre = domiciliarioNombre; }
    public Instant getAsignadoEn() { return asignadoEn; }
    public void setAsignadoEn(Instant asignadoEn) { this.asignadoEn = asignadoEn; }
    public Instant getEnCaminoEn() { return enCaminoEn; }
    public void setEnCaminoEn(Instant enCaminoEn) { this.enCaminoEn = enCaminoEn; }
    public Instant getEntregadoEn() { return entregadoEn; }
    public void setEntregadoEn(Instant entregadoEn) { this.entregadoEn = entregadoEn; }
    public Instant getCerradoEn() { return cerradoEn; }
    public void setCerradoEn(Instant cerradoEn) { this.cerradoEn = cerradoEn; }
    public Cobro getCobro() { return cobro; }
    public void setCobro(Cobro cobro) { this.cobro = cobro; }
    public String getMotivoNoEntrega() { return motivoNoEntrega; }
    public void setMotivoNoEntrega(String motivoNoEntrega) { this.motivoNoEntrega = motivoNoEntrega; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
    public List<CambioEstado> getHistorial() { return historial; }
    public void setHistorial(List<CambioEstado> historial) { this.historial = historial; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
