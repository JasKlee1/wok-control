package com.wokcontrol.menu.config;

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
                .requestMatchers(HttpMethod.GET, "/api/menu/**")
                    .hasAnyAuthority(MENU_VER, MENU_GESTIONAR, PEDIDOS_GESTIONAR, REPORTES_VER, SISTEMA)
                .requestMatchers(HttpMethod.POST, "/api/menu/cotizacion")
                    .hasAnyAuthority(PEDIDOS_GESTIONAR, MENU_VER, SISTEMA)
                .requestMatchers("/api/menu/**").hasAuthority(MENU_GESTIONAR)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers(HttpMethod.POST, "/menu/**").hasAuthority(MENU_GESTIONAR)
                .requestMatchers("/menu/categorias/**", "/menu/platos/nuevo", "/menu/platos/*/editar",
                        "/menu/combos/nuevo", "/menu/combos/*/editar").hasAuthority(MENU_GESTIONAR)
                .requestMatchers("/menu/**").hasAnyAuthority(MENU_VER, MENU_GESTIONAR)
                .anyRequest().authenticated());
        return http.build();
    }
}
