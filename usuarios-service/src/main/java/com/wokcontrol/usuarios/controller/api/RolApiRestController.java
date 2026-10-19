package com.wokcontrol.usuarios.controller.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.usuarios.dto.RolDtos.PermisoResponse;
import com.wokcontrol.usuarios.dto.RolDtos.RolRequest;
import com.wokcontrol.usuarios.dto.RolDtos.RolResponse;
import com.wokcontrol.usuarios.service.RolService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Roles y permisos", description = "Matriz de permisos; los 7 roles del sistema son de solo lectura")
public class RolApiRestController {

    private final RolService servicio;

    public RolApiRestController(RolService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/roles")
    @Operation(summary = "Listar roles con sus permisos")
    public List<RolResponse> listar() {
        return servicio.listar().stream().map(RolResponse::de).toList();
    }

    @GetMapping("/api/roles/{codigo}")
    @Operation(summary = "Consultar un rol")
    public RolResponse obtener(@PathVariable String codigo) {
        return RolResponse.de(servicio.obtener(codigo));
    }

    @PostMapping("/api/roles")
    @Operation(summary = "Crear rol personalizado", description = "Solo con permisos existentes del catálogo")
    public ResponseEntity<RolResponse> crear(@Valid @RequestBody RolRequest req) {
        RolResponse r = RolResponse.de(servicio.crear(req));
        return ResponseEntity.created(URI.create("/api/roles/" + r.codigo())).body(r);
    }

    @PutMapping("/api/roles/{codigo}")
    @Operation(summary = "Actualizar rol personalizado")
    public RolResponse actualizar(@PathVariable String codigo, @Valid @RequestBody RolRequest req) {
        return RolResponse.de(servicio.actualizar(codigo, req));
    }

    @GetMapping("/api/permisos")
    @Operation(summary = "Catálogo de permisos")
    public List<PermisoResponse> permisos() {
        return Permisos.DESCRIPCIONES.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> new PermisoResponse(e.getKey(), e.getValue())).toList();
    }
}
