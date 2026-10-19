package com.wokcontrol.common.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Usuario autenticado, construido exclusivamente a partir del JWT validado (no del navegador).
 */
public record UsuarioActual(String username, String nombre, List<String> roles, Set<String> permisos,
        String proveedorId, boolean servicio, String actor, String token) {

    public boolean tiene(String permiso) {
        return permisos.contains(permiso);
    }

    public boolean tieneAlguno(String... lista) {
        for (String p : lista) {
            if (permisos.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }

    /** Nombre legible del primer rol, para la barra superior. */
    public String rolPrincipal() {
        if (servicio) {
            return "Servicio interno";
        }
        return roles.stream().findFirst()
                .map(r -> Roles.SISTEMA.containsKey(r) ? Roles.SISTEMA.get(r).nombre() : r)
                .orElse("Sin rol");
    }

    /** Nombre a registrar en auditoría: para tokens de servicio, el usuario que originó la acción. */
    public String responsable() {
        return servicio ? actor : username;
    }

    public String iniciales() {
        String base = nombre == null || nombre.isBlank() ? username : nombre;
        String[] partes = base.trim().split("\\s+");
        String ini = partes[0].substring(0, 1) + (partes.length > 1 ? partes[1].substring(0, 1) : "");
        return ini.toUpperCase();
    }

    public static UsuarioActual desdeJwt(Jwt jwt) {
        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList(TokenService.CLAIM_ROLES)).orElse(List.of());
        List<String> permisos = Optional.ofNullable(jwt.getClaimAsStringList(TokenService.CLAIM_PERMISOS)).orElse(List.of());
        boolean servicio = "servicio".equals(jwt.getClaimAsString(TokenService.CLAIM_TIPO));
        return new UsuarioActual(jwt.getSubject(), jwt.getClaimAsString(TokenService.CLAIM_NOMBRE), roles,
                Set.copyOf(permisos), jwt.getClaimAsString(TokenService.CLAIM_PROVEEDOR), servicio,
                jwt.getClaimAsString(TokenService.CLAIM_ACTOR), jwt.getTokenValue());
    }

    public static Optional<UsuarioActual> opcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(desdeJwt(jwtAuth.getToken()));
        }
        return Optional.empty();
    }

    public static UsuarioActual requerido() {
        return opcional().orElseThrow(() -> new IllegalStateException("No hay un usuario autenticado"));
    }

    /** Usuario responsable de la operación en curso, o "sistema" si no hay autenticación. */
    public static String responsableActual() {
        return opcional().map(UsuarioActual::responsable).orElse("sistema");
    }
}
