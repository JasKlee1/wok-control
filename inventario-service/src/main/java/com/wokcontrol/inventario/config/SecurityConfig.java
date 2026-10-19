package com.wokcontrol.inventario.config;

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
                // operaciones internas: solo tokens de servicio
                .requestMatchers("/api/inventario/consumos/**", "/api/inventario/recepciones/**").hasAuthority(SISTEMA)
                .requestMatchers(HttpMethod.POST, "/api/insumos/*/reportes-agotado").hasAnyAuthority(INSUMOS_REPORTAR_AGOTADO, INVENTARIO_MOVIMIENTOS)
                .requestMatchers(HttpMethod.POST, "/api/inventario/reportes-agotado/*/atender").hasAuthority(INVENTARIO_MOVIMIENTOS)
                .requestMatchers(HttpMethod.POST, "/api/insumos/*/movimientos").hasAuthority(INVENTARIO_MOVIMIENTOS)
                .requestMatchers(HttpMethod.POST, "/api/insumos/*/ajustes").hasAuthority(INVENTARIO_AJUSTAR)
                .requestMatchers(HttpMethod.GET, "/api/insumos/resumen")
                    .hasAnyAuthority(INVENTARIO_VER, MENU_GESTIONAR, COMPRAS_GESTIONAR, PROVEEDORES_GESTIONAR, REPORTES_VER, SISTEMA)
                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyAuthority(INVENTARIO_VER, REPORTES_VER, SISTEMA)
                .requestMatchers("/api/insumos/**").hasAuthority(INVENTARIO_GESTIONAR)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers(HttpMethod.POST, "/inventario/insumos/*/movimientos", "/inventario/insumos/*/lotes/**",
                        "/inventario/alertas/**").hasAuthority(INVENTARIO_MOVIMIENTOS)
                .requestMatchers(HttpMethod.POST, "/inventario/insumos/*/ajuste").hasAuthority(INVENTARIO_AJUSTAR)
                .requestMatchers("/inventario/insumos/nuevo", "/inventario/insumos/*/editar", "/inventario/insumos/guardar",
                        "/inventario/insumos/*/estado").hasAuthority(INVENTARIO_GESTIONAR)
                .requestMatchers("/inventario/**").hasAuthority(INVENTARIO_VER)
                .anyRequest().authenticated());
        return http.build();
    }
}
