package com.wokcontrol.menu.controller.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.menu.dto.MenuDtos.CategoriaRequest;
import com.wokcontrol.menu.dto.MenuDtos.CategoriaResponse;
import com.wokcontrol.menu.service.CategoriaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/menu/categorias")
@Tag(name = "Categorías", description = "Secciones de la carta")
public class CategoriaApiRestController {

    private final CategoriaService servicio;

    public CategoriaApiRestController(CategoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar categorías")
    public List<CategoriaResponse> listar() {
        return servicio.listar().stream().map(CategoriaResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar categoría")
    public CategoriaResponse obtener(@PathVariable String id) {
        return CategoriaResponse.de(servicio.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear categoría", description = "Permiso MENU_GESTIONAR")
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest req) {
        CategoriaResponse r = CategoriaResponse.de(servicio.crear(req));
        return ResponseEntity.created(URI.create("/api/menu/categorias/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar categoría", description = "No se desactiva si tiene platos activos")
    public CategoriaResponse actualizar(@PathVariable String id, @Valid @RequestBody CategoriaRequest req) {
        return CategoriaResponse.de(servicio.actualizar(id, req));
    }
}
