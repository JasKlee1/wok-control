package com.wokcontrol.common.security;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Lee el JWT de la cookie HttpOnly para las rutas web. En las rutas públicas (login, recursos
 * estáticos) se ignora para que un token expirado no impida mostrar la página de inicio de sesión.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final String cookieName;

    public CookieBearerTokenResolver(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (esPublica(uri)) {
            return null;
        }
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    static boolean esPublica(String uri) {
        return uri.equals("/login") || uri.startsWith("/wok-static/") || uri.startsWith("/webjars/")
                || uri.equals("/error") || uri.equals("/favicon.ico") || uri.startsWith("/actuator/")
                || uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui");
    }
}
