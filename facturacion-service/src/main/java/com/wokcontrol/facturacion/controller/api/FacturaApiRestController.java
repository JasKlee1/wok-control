package com.wokcontrol.facturacion.controller.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.facturacion.dto.FacturacionDtos.AnulacionRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CobroRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CuentaMesa;
import com.wokcontrol.facturacion.dto.FacturacionDtos.FacturaResponse;
import com.wokcontrol.facturacion.dto.FacturacionDtos.ResumenVentas;
import com.wokcontrol.facturacion.entity.Factura;
import com.wokcontrol.facturacion.service.FacturacionService;
import com.wokcontrol.facturacion.service.ReporteVentasService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Facturación", description = "Cuentas por mesa, cobros y comprobantes internos de venta (no son factura electrónica DIAN)")
public class FacturaApiRestController {

    private final FacturacionService servicio;
    private final ReporteVentasService reportes;

    public FacturaApiRestController(FacturacionService servicio, ReporteVentasService reportes) {
        this.servicio = servicio;
        this.reportes = reportes;
    }

    @GetMapping("/api/cuentas/mesa/{mesaId}")
    @Operation(summary = "Cuenta de una mesa", description = "Pedidos entregados sin pagar; totales calculados en el servidor")
    public CuentaMesa cuenta(@PathVariable String mesaId) {
        return servicio.cuenta(mesaId);
    }

    @PostMapping("/api/facturas")
    @Operation(summary = "Cobrar y emitir comprobante interno",
            description = "Recalcula subtotal, descuento, impuesto al consumo y total. Valida que los pagos cubran el total. "
                    + "Repetir la solicitud con la misma claveIdempotencia devuelve el mismo comprobante")
    @ApiResponse(responseCode = "201", description = "Comprobante emitido")
    @ApiResponse(responseCode = "409", description = "Algún pedido ya fue cobrado")
    @ApiResponse(responseCode = "422", description = "Pagos insuficientes, descuento no autorizado o pedidos no entregados")
    public ResponseEntity<FacturaResponse> cobrar(@Valid @RequestBody CobroRequest req) {
        FacturaResponse r = FacturaResponse.de(servicio.cobrar(req));
        return ResponseEntity.created(URI.create("/api/facturas/" + r.id())).body(r);
    }

    @GetMapping("/api/facturas")
    @Operation(summary = "Historial de comprobantes", description = "Filtros por fechas, estado, cajero y mesa")
    public List<FacturaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Factura.Estado estado, @RequestParam(required = false) String cajero,
            @RequestParam(required = false) String mesaId) {
        return servicio.buscar(desde, hasta, estado, cajero, mesaId).stream().map(FacturaResponse::de).toList();
    }

    @GetMapping("/api/facturas/resumen")
    @Operation(summary = "Resumen de ventas", description = "Totales, métodos de pago, ventas por día y productos más vendidos")
    public ResumenVentas resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.resumen(desde, hasta);
    }

    @GetMapping("/api/facturas/{id}")
    @Operation(summary = "Consultar comprobante")
    public FacturaResponse obtener(@PathVariable String id) {
        return FacturaResponse.de(servicio.obtener(id));
    }

    @PostMapping("/api/facturas/{id}/anulacion")
    @Operation(summary = "Anular comprobante", description = "Con motivo; no se elimina. Los pedidos vuelven a quedar por cobrar")
    public FacturaResponse anular(@PathVariable String id, @Valid @RequestBody AnulacionRequest req) {
        return FacturaResponse.de(servicio.anular(id, req.motivo()));
    }

    @PostMapping("/api/facturas/{id}/sincronizacion")
    @Operation(summary = "Reintentar notificación a pedidos-service")
    public FacturaResponse sincronizar(@PathVariable String id) {
        return FacturaResponse.de(servicio.reintentar(id));
    }
}
