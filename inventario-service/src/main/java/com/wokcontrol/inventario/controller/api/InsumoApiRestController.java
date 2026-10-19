package com.wokcontrol.inventario.controller.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.inventario.dto.InventarioDtos.AjusteRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.InsumoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.InsumoResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.InsumoResumen;
import com.wokcontrol.inventario.dto.InventarioDtos.MovimientoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.MovimientoResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.ReporteAgotadoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.ReporteAgotadoResponse;
import com.wokcontrol.inventario.service.InsumoService;
import com.wokcontrol.inventario.service.MovimientoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/insumos")
@Tag(name = "Insumos", description = "Insumos, existencias y movimientos trazables")
public class InsumoApiRestController {

    private final InsumoService insumos;
    private final MovimientoService movimientos;

    public InsumoApiRestController(InsumoService insumos, MovimientoService movimientos) {
        this.insumos = insumos;
        this.movimientos = movimientos;
    }

    @GetMapping
    @Operation(summary = "Buscar insumos", description = "Filtros por nombre, categoría y stock bajo")
    public List<InsumoResponse> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "false") boolean bajoStock,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return insumos.buscar(q, categoria, bajoStock, incluirInactivos).stream().map(InsumoResponse::de).toList();
    }

    @GetMapping("/resumen")
    @Operation(summary = "Resumen de insumos activos", description = "Para recetas, compras y costos en otros servicios")
    public List<InsumoResumen> resumen() {
        return insumos.activos().stream().map(InsumoResumen::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar insumo")
    public InsumoResponse obtener(@PathVariable String id) {
        return InsumoResponse.de(insumos.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear insumo", description = "Nace con existencia 0; la cantidad inicial se registra con una ENTRADA")
    public ResponseEntity<InsumoResponse> crear(@Valid @RequestBody InsumoRequest req) {
        InsumoResponse r = InsumoResponse.de(insumos.crear(req));
        return ResponseEntity.created(URI.create("/api/insumos/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos del insumo", description = "No modifica la cantidad disponible")
    public InsumoResponse actualizar(@PathVariable String id, @Valid @RequestBody InsumoRequest req) {
        return InsumoResponse.de(insumos.actualizar(id, req));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar insumo")
    public InsumoResponse estado(@PathVariable String id, @RequestParam boolean activo) {
        return InsumoResponse.de(insumos.cambiarEstado(id, activo));
    }

    @PostMapping("/{id}/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar entrada, salida o merma",
            description = "Operación atómica: una salida o merma nunca deja existencias negativas")
    @ApiResponse(responseCode = "201", description = "Movimiento registrado con saldo anterior y nuevo")
    @ApiResponse(responseCode = "422", description = "Stock insuficiente o falta el motivo")
    public MovimientoResponse movimiento(@PathVariable String id, @Valid @RequestBody MovimientoRequest req) {
        return MovimientoResponse.de(movimientos.registrar(id, req));
    }

    @PostMapping("/{id}/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajuste por conteo físico", description = "Permiso INVENTARIO_AJUSTAR; queda auditado con motivo")
    public MovimientoResponse ajuste(@PathVariable String id, @Valid @RequestBody AjusteRequest req) {
        return MovimientoResponse.de(movimientos.ajustar(id, req));
    }

    @GetMapping("/{id}/movimientos")
    @Operation(summary = "Últimos movimientos del insumo")
    public List<MovimientoResponse> recientes(@PathVariable String id) {
        return movimientos.recientes(id).stream().map(MovimientoResponse::de).toList();
    }

    @PostMapping("/{id}/reportes-agotado")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reportar insumo agotado (cocina)")
    public ReporteAgotadoResponse reportar(@PathVariable String id, @Valid @RequestBody ReporteAgotadoRequest req) {
        return ReporteAgotadoResponse.de(insumos.reportarAgotado(id, req.observacion()));
    }
}
