package com.wokcontrol.pedidos.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/**
 * Integración con inventario-service. El consumo y su reversión son idempotentes por referencia
 * (PEDIDO-id), por eso se pueden reintentar con seguridad ante fallos de red.
 */
@Component
public class InventarioClient {

    private static final String SERVICIO = "inventario-service";
    private final ClienteInterno cliente;

    public InventarioClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record LineaConsumo(String insumoId, BigDecimal cantidad) {
    }

    public record ConsumoRequest(String referencia, String descripcion, List<LineaConsumo> lineas) {
    }

    public record ResultadoOperacion(String referencia, String estado, boolean yaProcesada, int lineas) {
    }

    public record InsumoAlerta(String id, String nombre, String unidad, BigDecimal cantidadDisponible, BigDecimal stockMinimo) {
    }

    public record ReporteCocina(String id, String insumoId, String insumo, String observacion, String reportadoPor,
            Instant fecha) {
    }

    public record AlertaVencimiento(String insumoId, String insumo, String lote, LocalDate fechaVencimiento, long diasRestantes) {
    }

    public record Alertas(List<InsumoAlerta> stockBajo, List<AlertaVencimiento> vencimientos,
            List<ReporteCocina> reportesCocina, int total) {
    }

    public record InsumoResumen(String id, String nombre, String unidad, String categoria, BigDecimal cantidadDisponible) {
    }

    public ResultadoOperacion consumir(String referencia, String descripcion, List<LineaConsumo> lineas) {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post()
                .uri("/api/inventario/consumos").headers(cliente.comoServicio())
                .body(new ConsumoRequest(referencia, descripcion, lineas)).retrieve().body(ResultadoOperacion.class));
    }

    public ResultadoOperacion revertir(String referencia) {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post()
                .uri("/api/inventario/consumos/{ref}/reversion", referencia).headers(cliente.comoServicio())
                .retrieve().body(ResultadoOperacion.class));
    }

    /** Alertas para la vista de cocina, con el token del cocinero (requiere INVENTARIO_VER). */
    public Alertas alertas() {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri("/api/inventario/alertas").headers(cliente.comoUsuario())
                .retrieve().body(Alertas.class));
    }

    public List<InsumoResumen> insumos() {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri("/api/insumos/resumen").headers(cliente.comoUsuario())
                .retrieve().body(new ParameterizedTypeReference<List<InsumoResumen>>() {
                }));
    }

    /** Reporte de agotado con el token del cocinero: inventario-service valida su permiso. */
    public void reportarAgotado(String insumoId, String observacion) {
        cliente.llamar(SERVICIO, false, () -> cliente.para(SERVICIO).post()
                .uri("/api/insumos/{id}/reportes-agotado", insumoId).headers(cliente.comoUsuario())
                .body(java.util.Map.of("observacion", observacion == null ? "" : observacion))
                .retrieve().toBodilessEntity());
    }
}
