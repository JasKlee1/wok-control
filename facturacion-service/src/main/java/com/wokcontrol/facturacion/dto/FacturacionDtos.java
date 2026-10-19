package com.wokcontrol.facturacion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.wokcontrol.facturacion.entity.AutorizacionDescuento;
import com.wokcontrol.facturacion.entity.CierreCaja;
import com.wokcontrol.facturacion.entity.Factura;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class FacturacionDtos {

    private FacturacionDtos() {
    }

    public static final BigDecimal DESCUENTO_MAXIMO = new BigDecimal("50");

    // ---------- Cuenta ----------
    public record LineaCuenta(String pedidoId, String pedidoNumero, String productoId, String descripcion, int cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    public record PedidoCuenta(String id, String numero, BigDecimal total, Instant entregadoEn) {
    }

    @Schema(description = "Cuenta calculada en el servidor con los pedidos entregados y no pagados de la mesa")
    public record CuentaMesa(String mesaId, int mesaNumero, List<PedidoCuenta> pedidos, List<LineaCuenta> lineas,
            BigDecimal subtotal, BigDecimal impuestoPorcentaje, BigDecimal impuestoValor, BigDecimal total,
            int pedidosEnCurso) {
    }

    // ---------- Cobro ----------
    public record PagoRequest(
            @NotNull(message = "Seleccione el método de pago") Factura.MetodoPago metodo,
            @NotNull(message = "Indique el monto") @DecimalMin(value = "1", message = "El monto debe ser positivo")
            @DecimalMax(value = "100000000") BigDecimal monto,
            @Size(max = 60) String referencia) {
    }

    public record DescuentoRequest(
            @NotNull @DecimalMin(value = "0.01", message = "El porcentaje debe ser positivo")
            @DecimalMax(value = "50", message = "El descuento máximo es 50%") BigDecimal porcentaje,
            @Size(max = 200) String motivo,
            @Size(max = 12) String codigoAutorizacion) {
    }

    @Schema(description = "Cobro de la cuenta. Los totales NO se envían: los calcula el servidor. "
            + "La clave de idempotencia evita cobrar dos veces si la solicitud se repite")
    public record CobroRequest(
            @NotBlank(message = "Indique la mesa") String mesaId,
            @Schema(description = "Pedidos a cobrar; si se omite se cobran todos los entregados de la mesa") List<String> pedidoIds,
            @NotEmpty(message = "Registre al menos un pago") @Valid List<PagoRequest> pagos,
            @Valid DescuentoRequest descuento,
            @Schema(example = "6f1c9a2e-...") @NotBlank(message = "Falta la clave de idempotencia")
            @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$") String claveIdempotencia) {
    }

    public record AnulacionRequest(@NotBlank(message = "Indique el motivo") @Size(min = 5, max = 200) String motivo) {
    }

    public record FacturaResponse(String id, String numero, String tipoDocumento, String mesaId, int mesaNumero,
            List<String> pedidoIds, List<Factura.Linea> lineas, BigDecimal subtotal, Factura.Descuento descuento,
            BigDecimal baseGravable, BigDecimal impuestoPorcentaje, BigDecimal impuestoValor, BigDecimal total,
            List<Factura.Pago> pagos, BigDecimal totalRecibido, BigDecimal cambio, Factura.Estado estado, String cajero,
            Instant emitidaEn, Factura.Anulacion anulacion, Factura.Sincronizacion sincronizacion, String cierreId) {

        public static FacturaResponse de(Factura f) {
            return new FacturaResponse(f.getId(), f.getNumero(), "COMPROBANTE_INTERNO_DE_VENTA", f.getMesaId(),
                    f.getMesaNumero(), f.getPedidoIds(), f.getLineas(), f.getSubtotal(), f.getDescuento(),
                    f.getBaseGravable(), f.getImpuestoPorcentaje(), f.getImpuestoValor(), f.getTotal(), f.getPagos(),
                    f.getTotalRecibido(), f.getCambio(), f.getEstado(), f.getCajero(), f.getEmitidaEn(), f.getAnulacion(),
                    f.getSincronizacion(), f.getCierreId());
        }
    }

    // ---------- Descuentos ----------
    public record AutorizacionRequest(
            @NotNull @DecimalMin(value = "1", message = "Mínimo 1%") @DecimalMax(value = "50", message = "Máximo 50%") BigDecimal porcentajeMaximo,
            @NotBlank(message = "Indique el motivo") @Size(min = 5, max = 200) String motivo,
            String mesaId,
            Integer horasVigencia) {
    }

    public record AutorizacionResponse(String codigo, BigDecimal porcentajeMaximo, String motivo, Integer mesaNumero,
            String autorizadoPor, Instant creadaEn, Instant expiraEn, boolean usada, String facturaId) {

        public static AutorizacionResponse de(AutorizacionDescuento a) {
            return new AutorizacionResponse(a.getCodigo(), a.getPorcentajeMaximo(), a.getMotivo(), a.getMesaNumero(),
                    a.getAutorizadoPor(), a.getCreadaEn(), a.getExpiraEn(), a.isUsada(), a.getFacturaId());
        }
    }

    // ---------- Caja ----------
    public record PreliminarCierre(String cajero, Instant desde, Instant hasta, int comprobantes, int anulados,
            BigDecimal totalVentas, BigDecimal totalDescuentos, BigDecimal totalImpuestos,
            Map<String, BigDecimal> sistemaPorMetodo) {
    }

    @Schema(description = "Montos contados físicamente por método de pago")
    public record CierreRequest(@NotNull Map<Factura.MetodoPago, @DecimalMin("0") BigDecimal> declarado,
            @Size(max = 300) String observaciones) {
    }

    public record CierreResponse(String id, String numero, String cajero, Instant desde, Instant hasta, int comprobantes,
            int anulados, BigDecimal totalVentas, Map<String, BigDecimal> sistemaPorMetodo,
            Map<String, BigDecimal> declaradoPorMetodo, Map<String, BigDecimal> diferenciaPorMetodo,
            BigDecimal diferenciaTotal, String observaciones, Instant creadoEn) {

        public static CierreResponse de(CierreCaja c) {
            return new CierreResponse(c.getId(), c.getNumero(), c.getCajero(), c.getDesde(), c.getHasta(), c.getComprobantes(),
                    c.getAnulados(), c.getTotalVentas(), c.getSistemaPorMetodo(), c.getDeclaradoPorMetodo(),
                    c.getDiferenciaPorMetodo(), c.getDiferenciaTotal(), c.getObservaciones(), c.getCreadoEn());
        }
    }

    // ---------- Reportes ----------
    public record VentaDia(LocalDate fecha, int comprobantes, BigDecimal total) {
    }

    public record ProductoVendido(String descripcion, int unidades, BigDecimal ventas) {
    }

    public record ResumenVentas(LocalDate desde, LocalDate hasta, int comprobantes, int anulados, BigDecimal ventasTotales,
            BigDecimal subtotal, BigDecimal descuentos, BigDecimal impuestos, BigDecimal ticketPromedio,
            Map<String, BigDecimal> porMetodo, List<VentaDia> porDia, List<ProductoVendido> productos,
            long pendientesSincronizar) {
    }
}
