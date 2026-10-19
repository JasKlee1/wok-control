package com.wokcontrol.usuarios.controller.api;

import java.util.ArrayList;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.usuarios.dto.AuthDtos.LoginRequest;
import com.wokcontrol.usuarios.dto.AuthDtos.LoginResponse;
import com.wokcontrol.usuarios.dto.AuthDtos.PerfilResponse;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.service.AuthService;
import com.wokcontrol.usuarios.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión y emisión de JWT")
public class AuthApiRestController {

    private final AuthService auth;
    private final UsuarioService usuarios;

    public AuthApiRestController(AuthService auth, UsuarioService usuarios) {
        this.auth = auth;
        this.usuarios = usuarios;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión", description = "Devuelve un JWT para usar como 'Authorization: Bearer <token>'. "
            + "Tras 5 intentos fallidos la cuenta se bloquea 15 minutos.")
    @ApiResponse(responseCode = "200", description = "Credenciales válidas")
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas, cuenta inactiva o bloqueada")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AuthService.Sesion s = auth.autenticar(req.username(), req.password());
        return new LoginResponse(s.token().token(), "Bearer", s.token().expiraEn(), s.perfil());
    }

    @GetMapping("/yo")
    @Operation(summary = "Datos del usuario autenticado")
    public PerfilResponse yo() {
        UsuarioActual actual = UsuarioActual.requerido();
        Usuario u = usuarios.obtenerPorUsername(actual.username());
        return new PerfilResponse(u.getUsername(), u.getNombre(), u.getEmail(), new ArrayList<>(u.getRoles()),
                actual.permisos(), u.getProveedorId(), u.isDebeCambiarPassword());
    }
}
