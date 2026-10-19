package com.wokcontrol.usuarios.dto;

import java.util.Set;

import com.wokcontrol.usuarios.entity.Rol;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RolDtos {

    private RolDtos() {
    }

    @Schema(description = "Rol personalizado compuesto por permisos del catálogo")
    public record RolRequest(
            @Schema(example = "JEFE_COCINA") @NotBlank(message = "El código es obligatorio")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "Use mayúsculas, números y guion bajo (3 a 40)") String codigo,
            @Schema(example = "Jefe de cocina") @NotBlank(message = "El nombre es obligatorio") @Size(max = 60) String nombre,
            @Size(max = 250) String descripcion,
            @NotEmpty(message = "Seleccione al menos un permiso") Set<String> permisos,
            boolean activo) {
    }

    public record RolResponse(String codigo, String nombre, String descripcion, Set<String> permisos,
            boolean sistema, boolean activo) {

        public static RolResponse de(Rol r) {
            return new RolResponse(r.getCodigo(), r.getNombre(), r.getDescripcion(), r.getPermisos(), r.isSistema(),
                    r.isActivo());
        }
    }

    public record PermisoResponse(String codigo, String descripcion) {
    }
}
