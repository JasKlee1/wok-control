package com.wokcontrol.pedidos.controller.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.pedidos.dto.PedidoDtos.ActualizarPedidoRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.CambioEstadoItemRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.CambioEstadoRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PagoPedidoRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoResponse;
import com.wokcontrol.pedidos.dto.PedidoDtos.ResumenPedidos;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.service.PedidoService;
import com.wokcontrol.pedidos.service.ResumenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Pedidos", description = "Registro de pedidos, comandas de cocina y estados")
public class PedidoApiRestController {

    private final PedidoService pedidos;
    private final ResumenService resumen;

    public PedidoApiRestController(PedidoService pedidos, ResumenService resumen) {
        this.pedidos = pedidos;
        this.resumen = resumen;
    }

    @GetMapping("/api/pedidos")
    @Operation(summary = "Buscar pedidos", description = "Filtros por estado, mesa, fecha (zona America/Bogota) y mesero")
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado,
            @RequestParam(required = false) String mesaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String mesero) {
        return pedidos.buscar(estado, mesaId, fecha, mesero).stream().map(PedidoResponse::de).toList();
    }

    @GetMapping("/api/pedidos/{id}")
    @Operation(summary = "Consultar pedido con historial de estados")
    public PedidoResponse obtener(@PathVariable String id) {
        return PedidoResponse.de(pedidos.obtener(id));
    }

    @GetMapping("/api/pedidos/facturables")
    @Operation(summary = "Pedidos entregados pendientes de pago de una mesa", description = "Usado por facturacion-service")
    public List<PedidoResponse> facturables(@RequestParam String mesaId) {
        return pedidos.facturables(mesaId).stream().map(PedidoResponse::de).toList();
    }

    @GetMapping("/api/pedidos/resumen")
    @Operation(summary = "Indicadores del día", description = "Pedidos, cocina, mesas y productos más vendidos")
    public ResumenPedidos resumen(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return resumen.resumen(fecha);
    }

    @PostMapping("/api/pedidos")
    @Operation(summary = "Registrar pedido", description = "Precios y disponibilidad se toman de menu-service; el pedido queda ABIERTO")
    @ApiResponse(responseCode = "201", description = "Pedido registrado")
    @ApiResponse(responseCode = "422", description = "Producto no disponible o mesa fuera de servicio")
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoRequest req) {
        PedidoResponse r = PedidoResponse.de(pedidos.crear(req));
        return ResponseEntity.created(URI.create("/api/pedidos/" + r.id())).body(r);
    }

    @PutMapping("/api/pedidos/{id}")
    @Operation(summary = "Modificar pedido abierto", description = "Solo antes de enviarlo a cocina")
    public PedidoResponse actualizar(@PathVariable String id, @Valid @RequestBody ActualizarPedidoRequest req) {
        return PedidoResponse.de(pedidos.actualizar(id, req));
    }

    @PatchMapping("/api/pedidos/{id}/estado")
    @Operation(summary = "Cambiar estado del pedido",
            description = "ENVIADO: envía a cocina y descuenta inventario (idempotente). CANCELADO: requiere motivo; "
                    + "solo antes de que cocina empiece. Los estados de preparación se cambian por ítem.")
    public PedidoResponse estado(@PathVariable String id, @Valid @RequestBody CambioEstadoRequest req) {
        return PedidoResponse.de(switch (req.estado()) {
            case ENVIADO -> pedidos.enviarACocina(id);
            case CANCELADO -> pedidos.cancelar(id, req.motivo());
            default -> throw new ReglaNegocioException("Use PATCH /api/pedidos/{id}/items/{itemId}/estado para avanzar la preparación; "
                    + "el pago lo registra facturación");
        });
    }

    @PatchMapping("/api/pedidos/{id}/items/{itemId}/estado")
    @Operation(summary = "Avanzar estado de un ítem",
            description = "Cocina: EN_PREPARACION, LISTO. Mesero: ENTREGADO. Solo transiciones consecutivas")
    public PedidoResponse estadoItem(@PathVariable String id, @PathVariable String itemId,
            @Valid @RequestBody CambioEstadoItemRequest req) {
        return PedidoResponse.de(pedidos.cambiarEstadoItem(id, itemId, req.estado()));
    }

    @GetMapping("/api/cocina/comandas")
    @Operation(summary = "Comandas en cocina", description = "Pedidos enviados, en preparación o listos, por orden de llegada")
    public List<PedidoResponse> comandas() {
        return pedidos.comandasCocina().stream().map(PedidoResponse::de).toList();
    }

    @PostMapping("/api/pedidos/{id}/pago")
    @Operation(summary = "Marcar pagado (interno)", description = "Solo facturacion-service; idempotente por comprobante")
    public PedidoResponse pago(@PathVariable String id, @Valid @RequestBody PagoPedidoRequest req) {
        return PedidoResponse.de(pedidos.marcarPagado(id, req.facturaId()));
    }

    @PostMapping("/api/pedidos/{id}/reversion-pago")
    @Operation(summary = "Revertir pago por anulación de comprobante (interno)")
    public PedidoResponse reversionPago(@PathVariable String id, @Valid @RequestBody PagoPedidoRequest req) {
        return PedidoResponse.de(pedidos.revertirPago(id, req.facturaId()));
    }
}
