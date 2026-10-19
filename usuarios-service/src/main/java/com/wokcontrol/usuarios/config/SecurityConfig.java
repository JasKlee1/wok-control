package com.wokcontrol.usuarios.config;

import static com.wokcontrol.common.security.Permisos.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.wokcontrol.common.security.JwtProperties;
import com.wokcontrol.common.security.WokSecurity;

/**
 * Autorización de usuarios-service. Las reglas se evalúan por permiso firmado en el JWT.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        // BCrypt por defecto, con prefijo {bcrypt} para poder migrar de algoritmo en el futuro
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        WokSecurity.api(http, decoder).authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/yo").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/usuarios/yo/password").authenticated()
                .requestMatchers("/api/usuarios/**").hasAuthority(USUARIOS_GESTIONAR)
                .requestMatchers(HttpMethod.GET, "/api/roles/**", "/api/permisos/**").hasAnyAuthority(USUARIOS_GESTIONAR, ROLES_GESTIONAR)
                .requestMatchers("/api/roles/**").hasAuthority(ROLES_GESTIONAR)
                .requestMatchers(HttpMethod.POST, "/api/auditoria/eventos").hasAuthority(SISTEMA)
                .requestMatchers(HttpMethod.GET, "/api/auditoria/**").hasAuthority(AUDITORIA_VER)
                .requestMatchers(HttpMethod.GET, "/api/parametros").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/parametros").hasAuthority(PARAMETROS_GESTIONAR)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers("/login").permitAll()
                .requestMatchers("/plataforma/usuarios/**").hasAuthority(USUARIOS_GESTIONAR)
                .requestMatchers("/plataforma/roles/**").hasAuthority(ROLES_GESTIONAR)
                .requestMatchers("/plataforma/auditoria/**").hasAuthority(AUDITORIA_VER)
                .requestMatchers("/plataforma/parametros/**").hasAuthority(PARAMETROS_GESTIONAR)
                .anyRequest().authenticated());
        return http.build();
    }
}
