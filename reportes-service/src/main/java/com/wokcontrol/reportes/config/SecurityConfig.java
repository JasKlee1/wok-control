package com.wokcontrol.reportes.config;

import static com.wokcontrol.common.security.Permisos.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
                .requestMatchers("/api/reportes/dashboard").hasAuthority(DASHBOARD_VER)
                .requestMatchers("/api/reportes/**").hasAuthority(REPORTES_VER)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers("/reportes/**").hasAuthority(REPORTES_VER)
                .requestMatchers("/", "/dashboard").authenticated()
                .anyRequest().authenticated());
        return http.build();
    }
}
