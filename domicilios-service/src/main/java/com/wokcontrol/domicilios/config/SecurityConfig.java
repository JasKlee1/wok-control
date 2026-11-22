package com.wokcontrol.domicilios.config;

import static com.wokcontrol.common.security.Permisos.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.wokcontrol.common.security.JwtProperties;
import com.wokcontrol.common.security.WokSecurity;

/**
 * v2 · HU-051 (REQ-WOK-051): el cajero (y el administrador) gestionan los domicilios con
 * DOMICILIOS_GESTIONAR; el domiciliario solo tiene DOMICILIOS_REPARTIR y el servicio comprueba,
 * además, que cada domicilio que consulta u opera esté asignado a él (AC-051.1).
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        WokSecurity.api(http, decoder).authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/domicilios/mios").hasAuthority(DOMICILIOS_REPARTIR)
                .requestMatchers(HttpMethod.POST, "/api/domicilios/*/en-camino", "/api/domicilios/*/entrega",
                        "/api/domicilios/*/no-entregado").hasAuthority(DOMICILIOS_REPARTIR)
                .requestMatchers(HttpMethod.GET, "/api/domicilios/domiciliarios").hasAuthority(DOMICILIOS_GESTIONAR)
                .requestMatchers(HttpMethod.GET, "/api/domicilios/*", "/api/domicilios/*/historial", "/api/domicilios/*/qr-ruta")
                    .hasAnyAuthority(DOMICILIOS_GESTIONAR, DOMICILIOS_REPARTIR)
                .requestMatchers("/api/domicilios", "/api/domicilios/**").hasAuthority(DOMICILIOS_GESTIONAR)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers("/mis-entregas", "/mis-entregas/**").hasAuthority(DOMICILIOS_REPARTIR)
                .requestMatchers(HttpMethod.GET, "/domicilios/*/qr-ruta.png").hasAnyAuthority(DOMICILIOS_GESTIONAR, DOMICILIOS_REPARTIR)
                .requestMatchers("/domicilios", "/domicilios/**").hasAuthority(DOMICILIOS_GESTIONAR)
                .anyRequest().authenticated());
        return http.build();
    }
}
