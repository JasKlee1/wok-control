package com.wokcontrol.pedidos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PedidoDtos {

    private PedidoDtos() {
    }

    public static final List<String> ALERGIAS = List.of("MANI", "MARISCOS", "PESCADO", "GLUTEN", "SOYA", "HUEVO",
            "LACTEOS", "AJONJOLI", "FRUTOS_SECOS");
    public static final List<String> ZONAS = List.of("SALON", "TERRAZA", "BARRA", "PRIVADO");

    // ---------- Mesas ----------
    public record MesaRequest(
            @Min(value = 1, message = "El número debe ser positivo") @Max(value = 999) int numero,
            @Min(value = 1, message = "Capacidad mínima 1") @Max(value = 30, message = "Capacidad máxima 30") int capacidad,
            @NotBlank(message = "Seleccione la zona") String zona,
            boolean activa) {
    }

    public record MesaResponse(String id, int numero, int capacidad, String zona, Mesa.Estado estado, boolean activa,
            Instant ocupadaDesde, int pedidosActivos, BigDecimal consumoActual) {
    }

    // ---------- Pedidos ----------
    @Schema(description = "Producto solicitado. El precio NO se envía: se toma de menu-service")
    public record ItemRequest(
            @NotNull(message = "Indique si es PLATO o COMBO") ItemPedido.Tipo tipo,
            @NotBlank(message = "Indique el producto") String productoId,
            @Min(value = 1, message = "La cantidad mínima es 1") @Max(value = 50, message = "Máximo 50 unidades") int cantidad,
            @Min(value = 0, message = "Picante entre 0 y 3") @Max(value = 3, message = "Picante entre 0 y 3") int nivelPicante,
            @Size(max = 200, message = "Máximo 200 caracteres") String notas) {
    }

    @Schema(description = "Registro de pedido para una mesa")
    public record PedidoRequest(
            @NotBlank(message = "Seleccione la mesa") String mesaId,
            @NotEmpty(message = "El pedido debe tener al menos un producto") @Valid List<ItemRequest> items,
            @Size(max = 300) String observaciones,
            Set<String> alergias) {
    }

    @Schema(description = "Modificación de un pedido que aún no se ha enviado a cocina")
    public record ActualizarPedidoRequest(
            @NotEmpty(message = "El pedido debe tener al menos un producto") @Valid List<ItemRequest> items,
            @Size(max = 300) String observaciones,
            Set<String> alergias) {
    }

    @Schema(description = "Cambio de estado del pedido: ENVIAR_COCINA (desde ABIERTO) o CANCELADO (con motivo)")
    public record CambioEstadoRequest(
            @NotNull(message = "Indique el estado destino") EstadoPedido estado,
            @Size(max = 200) String motivo) {
    }

    public record CambioEstadoItemRequest(@NotNull(message = "Indique el estado") ItemPedido.Estado estado) {
    }

    public record PagoPedidoRequest(@NotBlank String facturaId) {
    }

    public record ItemResponse(String id, ItemPedido.Tipo tipo, String productoId, String nombre, BigDecimal precioUnitario,
            int cantidad, BigDecimal subtotal, int nivelPicante, String notas, Set<String> alergenos,
            ItemPedido.Estado estado) {

        public static ItemResponse de(ItemPedido i) {
            return new ItemResponse(i.getId(), i.getTipo(), i.getProductoId(), i.getNombre(), i.getPrecioUnitario(),
                    i.getCantidad(), i.getSubtotal(), i.getNivelPicante(), i.getNotas(), i.getAlergenos(), i.getEstado());
        }
    }

    public record PedidoResponse(String id, String numero, String mesaId, int mesaNumero, String mesero,
            String meseroNombre, EstadoPedido estado, List<ItemResponse> items, BigDecimal total, String observaciones,
            Set<String> alergias, Pedido.EstadoConsumo consumoInventario, String facturaId, String canceladoPor,
            String motivoCancelacion, Instant creadoEn, Instant enviadoEn, Instant listoEn, Instant entregadoEn,
            Instant pagadoEn, List<Pedido.CambioEstado> historial) {

        public static PedidoResponse de(Pedido p) {
            return new PedidoResponse(p.getId(), p.getNumero(), p.getMesaId(), p.getMesaNumero(), p.getMesero(),
                    p.getMeseroNombre(), p.getEstado(), p.getItems().stream().map(ItemResponse::de).toList(), p.getTotal(),
                    p.getObservaciones(), p.getAlergias(), p.getConsumoInventario(), p.getFacturaId(), p.getCanceladoPor(),
                    p.getMotivoCancelacion(), p.getCreadoEn(), p.getEnviadoEn(), p.getListoEn(), p.getEntregadoEn(),
                    p.getPagadoEn(), p.getHistorial());
        }
    }

    // ---------- Resumen para el dashboard ----------
    public record ProductoVendido(String productoId, String nombre, int unidades, BigDecimal ventas) {
    }

    public record ResumenPedidos(long pedidosHoy, long pendientesCocina, long enPreparacion, long listos,
            long porCobrar, long cancelados, int mesasOcupadas, int mesasDisponibles, int mesasTotal,
            Double minutosPromedioPreparacion, List<ProductoVendido> masVendidos) {
    }
}
