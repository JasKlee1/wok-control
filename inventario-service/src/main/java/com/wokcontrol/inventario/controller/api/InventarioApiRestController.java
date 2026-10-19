package com.wokcontrol.inventario.controller.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.inventario.dto.InventarioDtos.AlertasResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.AtenderReporteRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.ConsumoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.MovimientoResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.RecepcionRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.ReporteAgotadoResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.ResultadoOperacion;
import com.wokcontrol.inventario.dto.InventarioDtos.ValorizacionResponse;
import com.wokcontrol.inventario.entity.MovimientoInventario;
import com.wokcontrol.inventario.service.InsumoService;
import com.wokcontrol.inventario.service.MovimientoService;
import com.wokcontrol.inventario.service.OperacionInternaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/inventario")
@Tag(name = "Inventario", description = "Historial, alertas, valorización y operaciones internas idempotentes")
public class InventarioApiRestController {

    private final MovimientoService movimientos;
    private final InsumoService insumos;
    private final OperacionInternaService operaciones;

    public InventarioApiRestController(MovimientoService movimientos, InsumoService insumos,
            OperacionInternaService operaciones) {
        this.movimientos = movimientos;
        this.insumos = insumos;
        this.operaciones = operaciones;
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Historial de movimientos", description = "Filtros por insumo, tipo, referencia y fechas; paginado")
    public Page<MovimientoResponse> historial(@RequestParam(required = false) String insumoId,
            @RequestParam(required = false) MovimientoInventario.Tipo tipo,
            @RequestParam(required = false) String referencia,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return movimientos.buscar(insumoId, tipo, referencia, desde, hasta, PageRequest.of(page, size))
                .map(MovimientoResponse::de);
    }

    @GetMapping("/alertas")
    @Operation(summary = "Alertas vigentes", description = "Stock bajo, lotes por vencer (7 días) y reportes de cocina")
    public AlertasResponse alertas() {
        return insumos.alertas();
    }

    @GetMapping("/valorizacion")
    @Operation(summary = "Valor del inventario a costo promedio")
    public ValorizacionResponse valorizacion() {
        return insumos.valorizacion();
    }

    @PostMapping("/reportes-agotado/{id}/atender")
    @Operation(summary = "Marcar reporte de cocina como atendido")
    public ReporteAgotadoResponse atender(@PathVariable String id, @Valid @RequestBody AtenderReporteRequest req) {
        return ReporteAgotadoResponse.de(insumos.atenderReporte(id, req.respuesta()));
    }

    @PostMapping("/consumos")
    @Operation(summary = "Registrar consumo de un pedido (interno)",
            description = "Idempotente por referencia. Solo token de servicio; bloqueado en el gateway")
    public ResultadoOperacion consumir(@Valid @RequestBody ConsumoRequest req) {
        return operaciones.consumir(req);
    }

    @PostMapping("/consumos/{referencia}/reversion")
    @Operation(summary = "Revertir consumo de un pedido cancelado (interno)", description = "Idempotente")
    public ResultadoOperacion revertir(@PathVariable String referencia) {
        return operaciones.revertirConsumo(referencia);
    }

    @PostMapping("/recepciones")
    @Operation(summary = "Ingresar mercancía de una orden de compra (interno)", description = "Idempotente por referencia")
    public ResultadoOperacion recibir(@Valid @RequestBody RecepcionRequest req) {
        return operaciones.recibir(req);
    }
}
