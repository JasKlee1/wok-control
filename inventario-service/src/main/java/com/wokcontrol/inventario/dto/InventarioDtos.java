package com.wokcontrol.inventario.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.MovimientoInventario;
import com.wokcontrol.inventario.entity.ReporteAgotado;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InventarioDtos {

    private InventarioDtos() {
    }

    public static final List<String> CATEGORIAS = List.of("PROTEINAS", "VEGETALES", "GRANOS", "FIDEOS", "SALSAS",
            "ESPECIAS", "ACEITES", "LACTEOS", "BEBIDAS", "DESECHABLES", "LIMPIEZA", "OTROS");
    public static final List<String> UNIDADES = List.of("KG", "G", "L", "ML", "UNIDAD", "PAQUETE");

    @Schema(description = "Alta o edición de insumo. La cantidad inicial se registra con un movimiento de ENTRADA")
    public record InsumoRequest(
            @Schema(example = "Arroz jazmín") @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String nombre,
            @Schema(example = "GRANOS") @NotBlank(message = "Seleccione la categoría") String categoria,
            @Schema(example = "KG") @NotBlank(message = "Seleccione la unidad") String unidad,
            @NotNull(message = "Indique el stock mínimo") @DecimalMin(value = "0", message = "No puede ser negativo")
            @DecimalMax(value = "1000000") BigDecimal stockMinimo,
            boolean perecedero) {
    }

    public record InsumoResponse(String id, String nombre, String categoria, String unidad, BigDecimal cantidadDisponible,
            BigDecimal stockMinimo, BigDecimal costoUnitario, BigDecimal valorizado, boolean bajoStock, boolean perecedero,
            boolean activo, List<Insumo.Lote> lotes, Instant actualizadoEn) {

        public static InsumoResponse de(Insumo i) {
            return new InsumoResponse(i.getId(), i.getNombre(), i.getCategoria(), i.getUnidad(), i.getCantidadDisponible(),
                    i.getStockMinimo(), i.getCostoUnitario(), i.getValorizado(), i.isBajoStock(), i.isPerecedero(),
                    i.isActivo(), i.getLotes(), i.getActualizadoEn());
        }
    }

    @Schema(description = "Vista reducida para otros servicios (recetas, compras, costos)")
    public record InsumoResumen(String id, String nombre, String unidad, String categoria, BigDecimal costoUnitario,
            BigDecimal cantidadDisponible, boolean activo) {

        public static InsumoResumen de(Insumo i) {
            return new InsumoResumen(i.getId(), i.getNombre(), i.getUnidad(), i.getCategoria(), i.getCostoUnitario(),
                    i.getCantidadDisponible(), i.isActivo());
        }
    }

    public enum TipoMovimientoManual { ENTRADA, SALIDA, MERMA }

    @Schema(description = "Movimiento manual. SALIDA y MERMA exigen motivo; ENTRADA puede registrar costo, lote y vencimiento")
    public record MovimientoRequest(
            @NotNull(message = "Seleccione el tipo") TipoMovimientoManual tipo,
            @NotNull(message = "Indique la cantidad") @DecimalMin(value = "0.0001", message = "La cantidad debe ser positiva")
            @DecimalMax(value = "1000000", message = "Cantidad fuera de rango") BigDecimal cantidad,
            @Size(max = 200) String motivo,
            @DecimalMin(value = "0", message = "El costo no puede ser negativo") BigDecimal costoUnitario,
            @Size(max = 40) String lote,
            LocalDate fechaVencimiento) {
    }

    @Schema(description = "Ajuste auditado por conteo físico: el sistema calcula la diferencia")
    public record AjusteRequest(
            @NotNull(message = "Indique la cantidad contada") @DecimalMin(value = "0", message = "No puede ser negativa") BigDecimal cantidadContada,
            @NotBlank(message = "El motivo del ajuste es obligatorio") @Size(min = 5, max = 200, message = "Entre 5 y 200 caracteres") String motivo) {
    }

    public record MovimientoResponse(String id, String insumoId, String nombreInsumo, String unidad,
            MovimientoInventario.Tipo tipo, BigDecimal cantidad, BigDecimal saldoAnterior, BigDecimal saldoNuevo,
            BigDecimal costoUnitario, String motivo, String usuario, Instant fecha, String referencia, String lote,
            LocalDate fechaVencimiento) {

        public static MovimientoResponse de(MovimientoInventario m) {
            return new MovimientoResponse(m.getId(), m.getInsumoId(), m.getNombreInsumo(), m.getUnidad(), m.getTipo(),
                    m.getCantidad(), m.getSaldoAnterior(), m.getSaldoNuevo(), m.getCostoUnitario(), m.getMotivo(),
                    m.getUsuario(), m.getFecha(), m.getReferencia(), m.getLote(), m.getFechaVencimiento());
        }
    }

    // ---------- Operaciones internas (servicio a servicio) ----------
    public record LineaConsumo(@NotBlank String insumoId,
            @NotNull @DecimalMin(value = "0.0001", message = "La cantidad debe ser positiva") BigDecimal cantidad) {
    }

    @Schema(description = "Consumo idempotente originado por un pedido (solo token de servicio)")
    public record ConsumoRequest(
            @Schema(example = "PEDIDO-66f1c0...") @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,80}$") String referencia,
            @Size(max = 200) String descripcion,
            @NotEmpty @Valid List<LineaConsumo> lineas) {
    }

    public record ResultadoOperacion(String referencia, String estado, boolean yaProcesada, int lineas) {
    }

    public record LineaRecepcion(@NotBlank String insumoId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal cantidad,
            @NotNull @DecimalMin(value = "0") BigDecimal costoUnitario,
            @Size(max = 40) String lote,
            LocalDate fechaVencimiento) {
    }

    @Schema(description = "Recepción de mercancía confirmada en proveedores-service (solo token de servicio)")
    public record RecepcionRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,80}$") String referencia,
            @NotBlank String proveedorId,
            @NotEmpty @Valid List<LineaRecepcion> lineas) {
    }

    // ---------- Alertas ----------
    public record ReporteAgotadoRequest(@Size(max = 200) String observacion) {
    }

    public record AtenderReporteRequest(@Size(max = 200) String respuesta) {
    }

    public record AlertaVencimiento(String insumoId, String insumo, String lote, LocalDate fechaVencimiento,
            long diasRestantes) {
    }

    public record ReporteAgotadoResponse(String id, String insumoId, String insumo, String observacion,
            String reportadoPor, Instant fecha, boolean atendido, String atendidoPor, Instant atendidoEn, String respuesta) {

        public static ReporteAgotadoResponse de(ReporteAgotado r) {
            return new ReporteAgotadoResponse(r.getId(), r.getInsumoId(), r.getNombreInsumo(), r.getObservacion(),
                    r.getReportadoPor(), r.getFecha(), r.isAtendido(), r.getAtendidoPor(), r.getAtendidoEn(), r.getRespuesta());
        }
    }

    @Schema(description = "Alertas vigentes: stock bajo, lotes por vencer y reportes de cocina sin atender")
    public record AlertasResponse(List<InsumoResponse> stockBajo, List<AlertaVencimiento> vencimientos,
            List<ReporteAgotadoResponse> reportesCocina, int total) {
    }

    public record ValorizacionResponse(BigDecimal valorTotal, long insumosActivos, long insumosBajoStock) {
    }
}
