package com.wokcontrol.menu.controller.api;

import java.net.URI;
import java.util.List;

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

import com.wokcontrol.menu.dto.MenuDtos.ComboRequest;
import com.wokcontrol.menu.dto.MenuDtos.ComboResponse;
import com.wokcontrol.menu.dto.MenuDtos.DisponibilidadRequest;
import com.wokcontrol.menu.service.ComboService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/menu/combos")
@Tag(name = "Combos", description = "Combinaciones de platos a precio conjunto")
public class ComboApiRestController {

    private final ComboService servicio;

    public ComboApiRestController(ComboService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar combos", description = "Incluye el precio de los platos por separado y la disponibilidad efectiva")
    public List<ComboResponse> listar(@RequestParam(defaultValue = "false") boolean incluirRetirados) {
        return servicio.listar(incluirRetirados).stream().map(servicio::aRespuesta).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar combo")
    public ComboResponse obtener(@PathVariable String id) {
        return servicio.aRespuesta(servicio.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear combo")
    public ResponseEntity<ComboResponse> crear(@Valid @RequestBody ComboRequest req) {
        ComboResponse r = servicio.aRespuesta(servicio.crear(req));
        return ResponseEntity.created(URI.create("/api/menu/combos/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar combo")
    public ComboResponse actualizar(@PathVariable String id, @Valid @RequestBody ComboRequest req) {
        return servicio.aRespuesta(servicio.actualizar(id, req));
    }

    @PatchMapping("/{id}/disponibilidad")
    @Operation(summary = "Marcar disponible o agotado")
    public ComboResponse disponibilidad(@PathVariable String id, @RequestBody DisponibilidadRequest req) {
        return servicio.aRespuesta(servicio.cambiarDisponibilidad(id, req.disponible()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirar combo (borrado lógico)")
    public void retirar(@PathVariable String id) {
        servicio.retirar(id);
    }
}
