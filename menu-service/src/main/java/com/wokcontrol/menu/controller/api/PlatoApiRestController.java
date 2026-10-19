package com.wokcontrol.menu.controller.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import com.wokcontrol.menu.dto.MenuDtos.DisponibilidadRequest;
import com.wokcontrol.menu.dto.MenuDtos.PlatoRequest;
import com.wokcontrol.menu.dto.MenuDtos.PlatoResponse;
import com.wokcontrol.menu.dto.MenuDtos.PrecioRequest;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.service.PlatoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/menu/platos")
@Tag(name = "Platos", description = "Platos de la carta, precios, recetas y disponibilidad")
public class PlatoApiRestController {

    private final PlatoService servicio;

    public PlatoApiRestController(PlatoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Buscar platos", description = "Filtros opcionales por nombre, categoría y disponibilidad")
    public List<PlatoResponse> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String categoriaId, @RequestParam(required = false) Boolean disponible,
            @RequestParam(defaultValue = "false") boolean incluirRetirados) {
        Map<String, String> cats = servicio.nombresCategorias();
        return servicio.buscar(q, categoriaId, disponible, incluirRetirados).stream()
                .map(p -> PlatoResponse.de(p, cats.get(p.getCategoriaId()))).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar plato con receta e historial de precios")
    @ApiResponse(responseCode = "404", description = "Plato inexistente")
    public PlatoResponse obtener(@PathVariable String id) {
        return respuesta(servicio.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear plato", description = "La receta se valida contra inventario-service")
    @ApiResponse(responseCode = "201", description = "Plato creado")
    @ApiResponse(responseCode = "409", description = "Nombre duplicado")
    public ResponseEntity<PlatoResponse> crear(@Valid @RequestBody PlatoRequest req) {
        PlatoResponse r = respuesta(servicio.crear(req));
        return ResponseEntity.created(URI.create("/api/menu/platos/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar plato", description = "Si cambia el precio queda registrado en el historial")
    public PlatoResponse actualizar(@PathVariable String id, @Valid @RequestBody PlatoRequest req) {
        return respuesta(servicio.actualizar(id, req));
    }

    @PatchMapping("/{id}/precio")
    @Operation(summary = "Cambiar precio")
    public PlatoResponse precio(@PathVariable String id, @Valid @RequestBody PrecioRequest req) {
        return respuesta(servicio.cambiarPrecio(id, req.precio()));
    }

    @PatchMapping("/{id}/disponibilidad")
    @Operation(summary = "Marcar disponible o agotado")
    public PlatoResponse disponibilidad(@PathVariable String id, @RequestBody DisponibilidadRequest req) {
        return respuesta(servicio.cambiarDisponibilidad(id, req.disponible()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirar plato de la carta", description = "Borrado lógico: se conserva para el historial de pedidos")
    public void retirar(@PathVariable String id) {
        servicio.retirar(id);
    }

    private PlatoResponse respuesta(Plato p) {
        return PlatoResponse.de(p, servicio.nombresCategorias().get(p.getCategoriaId()));
    }
}
