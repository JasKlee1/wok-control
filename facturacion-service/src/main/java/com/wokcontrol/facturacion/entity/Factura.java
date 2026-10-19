package com.wokcontrol.facturacion.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Comprobante interno de venta (tiquete). NO es una factura electrónica de venta ante la DIAN: el
 * sistema no está integrado con un proveedor tecnológico autorizado. Los valores se calculan en el
 * backend a partir de los pedidos y nunca se eliminan: un error se corrige anulando con motivo.
 */
@Document("facturas")
public class Factura {

    public enum Estado { EMITIDA, ANULADA }

    public enum Sincronizacion { PENDIENTE, COMPLETA, PENDIENTE_ANULACION }

    public enum MetodoPago {
        EFECTIVO("Efectivo"), TARJETA_DEBITO("Tarjeta débito"), TARJETA_CREDITO("Tarjeta crédito"),
        TRANSFERENCIA("Transferencia"), BILLETERA_DIGITAL("Billetera digital");

        private final String etiqueta;

        MetodoPago(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public record Linea(String pedidoId, String pedidoNumero, String productoId, String descripcion, int cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public record Descuento(BigDecimal porcentaje, BigDecimal valor, String motivo, String autorizadoPor,
            String codigoAutorizacion) {
    }

    public record Pago(MetodoPago metodo, BigDecimal monto, String referencia, String estado) {
    }

    public record DatosEmisor(String nombre, String nit, String direccion, String telefono, String mensaje) {
    }

    public record Anulacion(Instant fecha, String usuario, String motivo) {
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String numero;
    @Indexed(unique = true, sparse = true)
    private String claveIdempotencia;
    private String mesaId;
    private int mesaNumero;
    private List<String> pedidoIds = new ArrayList<>();
    private List<Linea> lineas = new ArrayList<>();
    private BigDecimal subtotal;
    private Descuento descuento;
    private BigDecimal baseGravable;
    private BigDecimal impuestoPorcentaje;
    private BigDecimal impuestoValor;
    private BigDecimal total;
    private List<Pago> pagos = new ArrayList<>();
    private BigDecimal totalRecibido;
    private BigDecimal cambio;
    @Indexed
    private Estado estado = Estado.EMITIDA;
    @Indexed
    private String cajero;
    private String cajeroNombre;
    @Indexed
    private Instant emitidaEn;
    private DatosEmisor emisor;
    private Anulacion anulacion;
    @Indexed
    private Sincronizacion sincronizacion = Sincronizacion.PENDIENTE;
    private int intentosSincronizacion;
    private String errorSincronizacion;
    @Indexed
    private String cierreId;
    @Version
    private Long version;

    /** Lo efectivamente recibido por método, neto del cambio entregado en efectivo. */
    public BigDecimal netoPorMetodo(MetodoPago metodo) {
        BigDecimal suma = pagos.stream().filter(p -> p.metodo() == metodo).map(Pago::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (metodo == MetodoPago.EFECTIVO && cambio != null) {
            suma = suma.subtract(cambio);
        }
        return suma;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public void setClaveIdempotencia(String c) { this.claveIdempotencia = c; }
    public String getMesaId() { return mesaId; }
    public void setMesaId(String mesaId) { this.mesaId = mesaId; }
    public int getMesaNumero() { return mesaNumero; }
    public void setMesaNumero(int mesaNumero) { this.mesaNumero = mesaNumero; }
    public List<String> getPedidoIds() { return pedidoIds; }
    public void setPedidoIds(List<String> pedidoIds) { this.pedidoIds = pedidoIds; }
    public List<Linea> getLineas() { return lineas; }
    public void setLineas(List<Linea> lineas) { this.lineas = lineas; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public Descuento getDescuento() { return descuento; }
    public void setDescuento(Descuento descuento) { this.descuento = descuento; }
    public BigDecimal getBaseGravable() { return baseGravable; }
    public void setBaseGravable(BigDecimal baseGravable) { this.baseGravable = baseGravable; }
    public BigDecimal getImpuestoPorcentaje() { return impuestoPorcentaje; }
    public void setImpuestoPorcentaje(BigDecimal v) { this.impuestoPorcentaje = v; }
    public BigDecimal getImpuestoValor() { return impuestoValor; }
    public void setImpuestoValor(BigDecimal impuestoValor) { this.impuestoValor = impuestoValor; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public List<Pago> getPagos() { return pagos; }
    public void setPagos(List<Pago> pagos) { this.pagos = pagos; }
    public BigDecimal getTotalRecibido() { return totalRecibido; }
    public void setTotalRecibido(BigDecimal totalRecibido) { this.totalRecibido = totalRecibido; }
    public BigDecimal getCambio() { return cambio; }
    public void setCambio(BigDecimal cambio) { this.cambio = cambio; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getCajero() { return cajero; }
    public void setCajero(String cajero) { this.cajero = cajero; }
    public String getCajeroNombre() { return cajeroNombre; }
    public void setCajeroNombre(String cajeroNombre) { this.cajeroNombre = cajeroNombre; }
    public Instant getEmitidaEn() { return emitidaEn; }
    public void setEmitidaEn(Instant emitidaEn) { this.emitidaEn = emitidaEn; }
    public DatosEmisor getEmisor() { return emisor; }
    public void setEmisor(DatosEmisor emisor) { this.emisor = emisor; }
    public Anulacion getAnulacion() { return anulacion; }
    public void setAnulacion(Anulacion anulacion) { this.anulacion = anulacion; }
    public Sincronizacion getSincronizacion() { return sincronizacion; }
    public void setSincronizacion(Sincronizacion s) { this.sincronizacion = s; }
    public int getIntentosSincronizacion() { return intentosSincronizacion; }
    public void setIntentosSincronizacion(int i) { this.intentosSincronizacion = i; }
    public String getErrorSincronizacion() { return errorSincronizacion; }
    public void setErrorSincronizacion(String e) { this.errorSincronizacion = e; }
    public String getCierreId() { return cierreId; }
    public void setCierreId(String cierreId) { this.cierreId = cierreId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
