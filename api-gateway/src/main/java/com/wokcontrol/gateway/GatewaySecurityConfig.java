package com.wokcontrol.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Seguridad perimetral del gateway:
 * <ul>
 * <li>Las rutas /api/** exigen un JWT válido (firma, emisor y expiración) antes de llegar a los
 * servicios, excepto el login. La autorización fina por permiso la aplica cada microservicio.</li>
 * <li>Los endpoints internos (servicio a servicio) se bloquean desde el exterior.</li>
 * <li>Las rutas web pasan: cada servicio valida la cookie de sesión y el token CSRF.</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /** Endpoints que solo se invocan entre microservicios con token de servicio. */
    static final String[] RUTAS_INTERNAS = {
            "/api/inventario/consumos/**", "/api/inventario/recepciones/**",
            "/api/auditoria/eventos/**", "/api/pedidos/*/pago", "/api/pedidos/*/reversion-pago" };

    @Bean
    SecurityWebFilterChain gatewayChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(c -> {
                })
                .authorizeExchange(a -> a
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(RUTAS_INTERNAS).denyAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                        .jwt(j -> j.jwtDecoder(decoder)))
                .build();
    }

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(@Value("${wok.security.jwt.secret}") String secret,
            @Value("${wok.security.jwt.issuer:wok-control}") String issuer) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${wok.cors.allowed-origins}") String origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
