package com.wokcontrol.usuarios.dto;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import com.wokcontrol.usuarios.entity.Usuario;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    @Schema(description = "Alta de usuario")
    public record CrearUsuarioRequest(
            @Schema(example = "mesero1") @NotBlank(message = "El usuario es obligatorio")
            @Pattern(regexp = "^[a-z0-9._-]{3,40}$", message = "Use de 3 a 40 caracteres: minúsculas, números, punto, guion o guion bajo")
            String username,
            @Schema(example = "Ana Gómez") @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
            @Schema(example = "ana@restaurante.co") @Email(message = "Correo inválido") @Size(max = 120) String email,
            @NotBlank(message = "La contraseña inicial es obligatoria") @Size(min = 8, max = 120, message = "Debe tener entre 8 y 120 caracteres") String password,
            @Schema(example = "[\"MESERO\"]") @NotEmpty(message = "Asigne al menos un rol") Set<String> roles,
            @Schema(description = "Obligatorio para el rol PROVEEDOR") @Size(max = 40) String proveedorId) {
    }

    @Schema(description = "Actualización de datos y roles (la contraseña se gestiona aparte)")
    public record ActualizarUsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
            @Email(message = "Correo inválido") @Size(max = 120) String email,
            @NotEmpty(message = "Asigne al menos un rol") Set<String> roles,
            @Size(max = 40) String proveedorId) {
    }

    public record EstadoRequest(boolean activo) {
    }

    public record RestablecerPasswordRequest(
            @NotBlank @Size(min = 8, max = 120, message = "Debe tener entre 8 y 120 caracteres") String nuevaPassword) {
    }

    @Schema(description = "Usuario sin datos sensibles (nunca incluye el hash de la contraseña)")
    public record UsuarioResponse(String id, String username, String nombre, String email, Set<String> roles,
            String proveedorId, boolean activo, boolean bloqueado, boolean debeCambiarPassword, Instant ultimoAcceso,
            Instant creadoEn) {

        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getUsername(), u.getNombre(), u.getEmail(),
                    new LinkedHashSet<>(u.getRoles()), u.getProveedorId(), u.isActivo(), u.estaBloqueado(Instant.now()),
                    u.isDebeCambiarPassword(), u.getUltimoAcceso(), u.getCreadoEn());
        }
    }
}
