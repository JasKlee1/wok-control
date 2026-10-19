package com.wokcontrol.pedidos.config;

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

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        WokSecurity.api(http, decoder).authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/pedidos/*/pago", "/api/pedidos/*/reversion-pago").hasAuthority(SISTEMA)
                .requestMatchers(HttpMethod.GET, "/api/mesas/**").hasAnyAuthority(MESAS_VER, MESAS_GESTIONAR, FACTURACION_VER, REPORTES_VER, SISTEMA)
                .requestMatchers(HttpMethod.PATCH, "/api/mesas/*/liberar").hasAnyAuthority(PEDIDOS_GESTIONAR, MESAS_GESTIONAR)
                .requestMatchers("/api/mesas/**").hasAuthority(MESAS_GESTIONAR)
                .requestMatchers(HttpMethod.GET, "/api/cocina/**").hasAnyAuthority(COCINA_OPERAR, PEDIDOS_VER)
                .requestMatchers(HttpMethod.PATCH, "/api/pedidos/*/items/**").hasAnyAuthority(COCINA_OPERAR, PEDIDOS_GESTIONAR)
                .requestMatchers(HttpMethod.GET, "/api/pedidos/**").hasAnyAuthority(PEDIDOS_VER, FACTURACION_VER, REPORTES_VER, SISTEMA)
                .requestMatchers("/api/pedidos/**").hasAuthority(PEDIDOS_GESTIONAR)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers("/mesas/nueva", "/mesas/*/editar", "/mesas/guardar", "/mesas/*/servicio").hasAuthority(MESAS_GESTIONAR)
                .requestMatchers(HttpMethod.POST, "/mesas/*/liberar").hasAnyAuthority(PEDIDOS_GESTIONAR, MESAS_GESTIONAR)
                .requestMatchers("/mesas/**").hasAnyAuthority(MESAS_VER, MESAS_GESTIONAR)
                .requestMatchers(HttpMethod.POST, "/cocina/**").hasAuthority(COCINA_OPERAR)
                .requestMatchers("/cocina/**").hasAuthority(COCINA_OPERAR)
                .requestMatchers(HttpMethod.POST, "/pedidos/*/items/**").hasAnyAuthority(COCINA_OPERAR, PEDIDOS_GESTIONAR)
                .requestMatchers("/pedidos/nuevo", "/pedidos/*/editar").hasAuthority(PEDIDOS_GESTIONAR)
                .requestMatchers(HttpMethod.POST, "/pedidos/**").hasAuthority(PEDIDOS_GESTIONAR)
                .requestMatchers("/pedidos/**").hasAuthority(PEDIDOS_VER)
                .anyRequest().authenticated());
        return http.build();
    }
}
