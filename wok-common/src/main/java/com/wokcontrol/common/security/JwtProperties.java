package com.wokcontrol.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del JWT. El secreto llega por la variable de entorno JWT_SECRET (nunca en el repositorio).
 */
@ConfigurationProperties(prefix = "wok.security.jwt")
public record JwtProperties(
        String secret,
        Long expirationMinutes,
        String issuer,
        String cookieName,
        Boolean cookieSecure,
        Long serviceTokenMinutes) {

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "wok.security.jwt.secret (JWT_SECRET) es obligatorio y debe tener al menos 32 bytes");
        }
        expirationMinutes = expirationMinutes == null ? 480L : expirationMinutes;
        issuer = issuer == null ? "wok-control" : issuer;
        cookieName = cookieName == null ? "WOK_TOKEN" : cookieName;
        cookieSecure = cookieSecure != null && cookieSecure;
        serviceTokenMinutes = serviceTokenMinutes == null ? 5L : serviceTokenMinutes;
    }
}
