package com.wokcontrol.usuarios.controller.api;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.usuarios.dto.AuthDtos.CambioPasswordRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.ActualizarUsuarioRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.CrearUsuarioRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.EstadoRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.RestablecerPasswordRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.UsuarioResponse;
import com.wokcontrol.usuarios.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Gestión de cuentas (permiso USUARIOS_GESTIONAR)")
public class UsuarioApiRestController {

    private final UsuarioService servicio;

    public UsuarioApiRestController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Buscar usuarios", description = "Filtros opcionales por texto, rol y estado; paginado")
    public Page<UsuarioResponse> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String rol, @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return servicio.buscar(q, rol, activo, PageRequest.of(page, size)).map(UsuarioResponse::de);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un usuario")
    public UsuarioResponse obtener(@PathVariable String id) {
        return UsuarioResponse.de(servicio.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear usuario", description = "La contraseña inicial debe cambiarse en el primer ingreso")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest req) {
        UsuarioResponse r = UsuarioResponse.de(servicio.crear(req, UsuarioActual.responsableActual()));
        return ResponseEntity.created(URI.create("/api/usuarios/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos y roles")
    public UsuarioResponse actualizar(@PathVariable String id, @Valid @RequestBody ActualizarUsuarioRequest req) {
        return UsuarioResponse.de(servicio.actualizar(id, req, UsuarioActual.responsableActual()));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar una cuenta", description = "No se puede desactivar la propia cuenta ni al último administrador de plataforma")
    public UsuarioResponse estado(@PathVariable String id, @RequestBody EstadoRequest req) {
        return UsuarioResponse.de(servicio.cambiarEstado(id, req.activo(), UsuarioActual.responsableActual()));
    }

    @PostMapping("/{id}/restablecer-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restablecer contraseña", description = "El usuario deberá cambiarla al ingresar")
    public void restablecer(@PathVariable String id, @Valid @RequestBody RestablecerPasswordRequest req) {
        servicio.restablecerPassword(id, req.nuevaPassword());
    }

    @PutMapping("/yo/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar mi contraseña", description = "Disponible para cualquier usuario autenticado")
    public void cambiarPropia(@Valid @RequestBody CambioPasswordRequest req) {
        servicio.cambiarPasswordPropia(UsuarioActual.requerido().username(), req.actual(), req.nueva(), req.confirmacion());
    }
}
