package com.wokcontrol.usuarios.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "Credenciales de acceso")
    public record LoginRequest(
            @Schema(example = "mesero1") @NotBlank(message = "El usuario es obligatorio") @Size(max = 60) String username,
            @Schema(example = "********") @NotBlank(message = "La contraseña es obligatoria") @Size(max = 120) String password) {
    }

    @Schema(description = "Token emitido tras un inicio de sesión exitoso")
    public record LoginResponse(
            @Schema(description = "JWT firmado HS256 para la cabecera Authorization: Bearer") String token,
            @Schema(example = "Bearer") String tipo,
            Instant expiraEn,
            PerfilResponse usuario) {
    }

    @Schema(description = "Datos del usuario autenticado")
    public record PerfilResponse(String username, String nombre, String email, List<String> roles,
            Set<String> permisos, String proveedorId, boolean debeCambiarPassword) {
    }

    public record CambioPasswordRequest(
            @NotBlank(message = "Ingrese la contraseña actual") String actual,
            @NotBlank(message = "Ingrese la nueva contraseña") @Size(min = 8, max = 120, message = "Debe tener entre 8 y 120 caracteres") String nueva,
            @NotBlank(message = "Confirme la nueva contraseña") String confirmacion) {
    }
}
