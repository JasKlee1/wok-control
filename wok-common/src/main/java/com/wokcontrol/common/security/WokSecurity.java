package com.wokcontrol.common.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Configuración base reutilizada por todos los microservicios. Cada servicio agrega sus propias
 * reglas de autorización por permiso sobre estas dos cadenas:
 * <ul>
 * <li><b>API</b> (/api/**): sin estado, JWT en cabecera Authorization: Bearer, sin CSRF (no usa cookies)
 * y respuestas de error en JSON.</li>
 * <li><b>Web</b> (Thymeleaf): JWT en cookie HttpOnly, CSRF con cookie de doble envío, redirección al
 * login y a la página de acceso denegado.</li>
 * </ul>
 */
public final class WokSecurity {

    public static final String[] RUTAS_PUBLICAS = {
            "/wok-static/**", "/webjars/**", "/error", "/favicon.ico",
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html" };

    private WokSecurity() {
    }

    public static HttpSecurity api(HttpSecurity http, JwtDecoder decoder) throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> escribirJson(res, HttpStatus.UNAUTHORIZED,
                "No autenticado", "Se requiere un token JWT válido en la cabecera Authorization", req.getRequestURI());
        AccessDeniedHandler denied = (req, res, ex) -> escribirJson(res, HttpStatus.FORBIDDEN,
                "Acceso denegado", "Su rol no tiene permiso para esta operación", req.getRequestURI());
        return http.securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(new DefaultBearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(denied)
                        .jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(new WokJwtAuthenticationConverter())))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
    }

    public static HttpSecurity web(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> {
            if (req.getRequestURI().startsWith("/login")) {
                // Este servicio no tiene página de login: se accedió por su puerto interno y no por el
                // gateway. Se informa en lugar de redirigir, para no entrar en un bucle infinito.
                res.setStatus(HttpStatus.NOT_FOUND.value());
                res.setContentType(MediaType.TEXT_HTML_VALUE);
                res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                res.getWriter().write("<!DOCTYPE html><html lang=\"es\"><meta charset=\"UTF-8\"><title>Wok Control</title>"
                        + "<body style=\"font-family:system-ui;background:#FFF8EE;color:#242424;padding:3rem;text-align:center\">"
                        + "<h1 style=\"color:#A61B1B\">Acceda por el API Gateway</h1>"
                        + "<p>Está abriendo un microservicio por su puerto interno. La aplicación web se usa a través del gateway:</p>"
                        + "<p><a style=\"color:#A61B1B;font-size:1.3rem\" href=\"http://localhost:8080/login\">http://localhost:8080</a></p>"
                        + "</body></html>");
                return;
            }
            boolean teniaToken = tieneCookie(req, props.cookieName());
            if (teniaToken) {
                borrarCookie(res, props.cookieName(), props.cookieSecure());
            }
            String destino = req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
            String url = "/login?" + (teniaToken ? "expirada&" : "") + "continuar="
                    + URLEncoder.encode(destino, StandardCharsets.UTF_8);
            res.sendRedirect(url);
        };
        AccessDeniedHandler denied = (req, res, ex) -> res.sendRedirect("/acceso-denegado");
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.path("/").sameSite("Lax").secure(props.cookieSecure()));
        return http
                .csrf(c -> c.csrfTokenRepository(csrfRepo))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(h -> h.frameOptions(f -> f.deny()))
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(new CookieBearerTokenResolver(props.cookieName()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(denied)
                        .jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(new WokJwtAuthenticationConverter())))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
    }

    public static Cookie cookieToken(String nombre, String valor, int maxAgeSegundos, boolean secure) {
        Cookie c = new Cookie(nombre, valor);
        c.setHttpOnly(true);
        c.setSecure(secure);
        c.setPath("/");
        c.setMaxAge(maxAgeSegundos);
        c.setAttribute("SameSite", "Lax");
        return c;
    }

    public static void borrarCookie(HttpServletResponse res, String nombre, boolean secure) {
        res.addCookie(cookieToken(nombre, "", 0, secure));
    }

    private static boolean tieneCookie(HttpServletRequest req, String nombre) {
        if (req.getCookies() == null) {
            return false;
        }
        for (Cookie c : req.getCookies()) {
            if (nombre.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    static void escribirJson(HttpServletResponse res, HttpStatus status, String titulo, String detalle,
            String instancia) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detalle + "\",\"instance\":\"" + instancia.replace("\"", "")
                + "\",\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
