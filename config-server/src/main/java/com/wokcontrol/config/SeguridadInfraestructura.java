package com.wokcontrol.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * En Render cada servicio tiene URL pública: con el perfil "render" el servidor de configuración exige usuario y
 * contraseña (WOK_INFRA_USER / WOK_INFRA_PASSWORD). La configuración no contiene
 * secretos, pero describe la arquitectura interna. En local queda abierto, como antes.
 */
@Configuration
public class SeguridadInfraestructura {

    @Bean
    @Profile("render")
    SecurityFilterChain protegida(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(withDefaults())
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Profile("!render")
    SecurityFilterChain abierta(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable()).build();
    }
}
