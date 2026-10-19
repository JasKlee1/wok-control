package com.wokcontrol.proveedores.config;

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
 * Un proveedor solo accede a /mi-perfil y /mias; el servicio además verifica que cada orden le
 * pertenezca (claim proveedorId firmado en el JWT).
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        WokSecurity.api(http, decoder).authorizeHttpRequests(a -> a
                .requestMatchers("/api/proveedores/mi-perfil", "/api/ordenes-compra/mias", "/api/ordenes-compra/mias/**")
                    .hasAuthority(PORTAL_PROVEEDOR)
                .requestMatchers(HttpMethod.POST, "/api/ordenes-compra/*/confirmacion", "/api/ordenes-compra/*/entrega")
                    .hasAnyAuthority(PORTAL_PROVEEDOR, COMPRAS_GESTIONAR)
                .requestMatchers(HttpMethod.GET, "/api/proveedores/resumen").hasAnyAuthority(PROVEEDORES_VER, COMPRAS_GESTIONAR, SISTEMA)
                .requestMatchers(HttpMethod.GET, "/api/proveedores/**").hasAnyAuthority(PROVEEDORES_VER, COMPRAS_GESTIONAR)
                .requestMatchers("/api/proveedores/**").hasAuthority(PROVEEDORES_GESTIONAR)
                .requestMatchers(HttpMethod.GET, "/api/ordenes-compra/**").hasAnyAuthority(COMPRAS_GESTIONAR, PROVEEDORES_VER, REPORTES_VER)
                .requestMatchers("/api/ordenes-compra/**").hasAuthority(COMPRAS_GESTIONAR)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers("/portal-proveedor/**").hasAuthority(PORTAL_PROVEEDOR)
                .requestMatchers("/proveedores/nuevo", "/proveedores/*/editar").hasAuthority(PROVEEDORES_GESTIONAR)
                .requestMatchers(HttpMethod.POST, "/proveedores/**").hasAuthority(PROVEEDORES_GESTIONAR)
                .requestMatchers("/proveedores/**").hasAnyAuthority(PROVEEDORES_VER, COMPRAS_GESTIONAR)
                .requestMatchers("/compras/ordenes/nueva", "/compras/ordenes/*/editar").hasAuthority(COMPRAS_GESTIONAR)
                .requestMatchers(HttpMethod.POST, "/compras/**").hasAuthority(COMPRAS_GESTIONAR)
                .requestMatchers("/compras/**").hasAnyAuthority(COMPRAS_GESTIONAR, PROVEEDORES_VER)
                .anyRequest().authenticated());
        return http.build();
    }
}
