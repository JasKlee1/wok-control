package com.wokcontrol.reportes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Vistas reducidas de las respuestas de otros servicios (solo los campos que usa el panel) y los
 * resultados agregados. Los campos adicionales de las respuestas remotas se ignoran.
 */
public final class ReportesDtos {

    private ReportesDtos() {
    }

    // ---------- facturacion-service ----------
    public record VentaDia(LocalDate fecha, int comprobantes, BigDecimal total) {
    }

    public record ProductoVendido(String descripcion, int unidades, BigDecimal ventas) {
    }

    public record ResumenVentas(LocalDate desde, LocalDate hasta, int comprobantes, int anulados, BigDecimal ventasTotales,
            BigDecimal subtotal, BigDecimal descuentos, BigDecimal impuestos, BigDecimal ticketPromedio,
            Map<String, BigDecimal> porMetodo, List<VentaDia> porDia, List<ProductoVendido> productos,
            long pendientesSincronizar) {
    }

    // ---------- pedidos-service ----------
    public record ProductoPedido(String productoId, String nombre, int unidades, BigDecimal ventas) {
    }

    public record ResumenPedidos(long pedidosHoy, long pendientesCocina, long enPreparacion, long listos, long porCobrar,
            long cancelados, int mesasOcupadas, int mesasDisponibles, int mesasTotal, Double minutosPromedioPreparacion,
            List<ProductoPedido> masVendidos) {
    }

    // ---------- inventario-service ----------
    public record InsumoAlerta(String id, String nombre, String unidad, BigDecimal cantidadDisponible, BigDecimal stockMinimo) {
    }

    public record ReporteCocina(String id, String insumo, String reportadoPor) {
    }

    public record Vencimiento(String insumo, String lote, LocalDate fechaVencimiento, long diasRestantes) {
    }

    public record Alertas(List<InsumoAlerta> stockBajo, List<Vencimiento> vencimientos, List<ReporteCocina> reportesCocina,
            int total) {
    }

    public record Valorizacion(BigDecimal valorTotal, long insumosActivos, long insumosBajoStock) {
    }

    public record InsumoResumen(String id, String nombre, String unidad, BigDecimal costoUnitario) {
    }

    // ---------- proveedores-service ----------
    public record ResumenCompras(long borradores, long enviadas, long confirmadas, long entregaReportada,
            long pendientesRecepcion, long recepcionesSinSincronizar, BigDecimal comprasRecibidasMes, long proveedoresActivos) {
    }

    // ---------- menu-service ----------
    public record IngredienteReceta(String insumoId, String nombreInsumo, String unidad, BigDecimal cantidad) {
    }

    public record Plato(String id, String nombre, String categoria, BigDecimal precio, boolean disponible,
            List<IngredienteReceta> receta) {
    }

    // ---------- resultados ----------
    /** Resultado de una sección del panel: datos o el motivo por el que no se pudieron obtener. */
    public record Seccion<T>(T datos, String error) {
        public static <T> Seccion<T> ok(T datos) {
            return new Seccion<>(datos, null);
        }

        public static <T> Seccion<T> fallo(String error) {
            return new Seccion<>(null, error);
        }

        public boolean disponible() {
            return datos != null;
        }
    }

    public record Dashboard(Seccion<ResumenVentas> ventas, Seccion<ResumenPedidos> pedidos, Seccion<Alertas> alertas,
            Seccion<Valorizacion> inventario, Seccion<ResumenCompras> compras) {
    }

    public record CostoPlato(String id, String nombre, String categoria, BigDecimal precio, BigDecimal costo,
            BigDecimal margen, BigDecimal margenPorcentaje, boolean recetaCompleta, int ingredientes) {

        /** Clase visual del margen: ≥ 60% saludable, 40–60% vigilar, &lt; 40% bajo. */
        public String claseMargen() {
            if (margenPorcentaje == null) {
                return "neutro";
            }
            return margenPorcentaje.compareTo(BigDecimal.valueOf(60)) >= 0 ? "exito"
                    : margenPorcentaje.compareTo(BigDecimal.valueOf(40)) >= 0 ? "alerta" : "error";
        }
    }

    /** Barra del gráfico de ventas por día con su ancho relativo ya calculado. */
    public record BarraDia(LocalDate fecha, int comprobantes, BigDecimal total, int porcentaje) {
    }
}
