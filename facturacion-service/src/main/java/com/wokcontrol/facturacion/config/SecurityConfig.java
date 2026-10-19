package com.wokcontrol.facturacion.config;

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
                .requestMatchers(HttpMethod.GET, "/api/cuentas/**").hasAnyAuthority(FACTURACION_COBRAR, FACTURACION_VER)
                .requestMatchers(HttpMethod.POST, "/api/facturas").hasAuthority(FACTURACION_COBRAR)
                .requestMatchers(HttpMethod.POST, "/api/facturas/*/anulacion").hasAuthority(FACTURAS_ANULAR)
                .requestMatchers(HttpMethod.POST, "/api/facturas/*/sincronizacion").hasAnyAuthority(FACTURACION_COBRAR, FACTURAS_ANULAR)
                .requestMatchers(HttpMethod.GET, "/api/facturas/**").hasAnyAuthority(FACTURACION_VER, REPORTES_VER)
                .requestMatchers(HttpMethod.POST, "/api/descuentos/autorizaciones").hasAuthority(DESCUENTOS_AUTORIZAR)
                .requestMatchers(HttpMethod.GET, "/api/descuentos/**").hasAuthority(DESCUENTOS_AUTORIZAR)
                .requestMatchers(HttpMethod.GET, "/api/caja/cierres/preliminar").hasAuthority(CAJA_CERRAR)
                .requestMatchers(HttpMethod.POST, "/api/caja/cierres").hasAuthority(CAJA_CERRAR)
                .requestMatchers(HttpMethod.GET, "/api/caja/**").hasAnyAuthority(CAJA_CERRAR, FACTURACION_VER, REPORTES_VER)
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http, JwtDecoder decoder, JwtProperties props) throws Exception {
        WokSecurity.web(http, decoder, props).authorizeHttpRequests(a -> a
                .requestMatchers(WokSecurity.RUTAS_PUBLICAS).permitAll()
                .requestMatchers("/caja/cuentas/**").hasAuthority(FACTURACION_COBRAR)
                .requestMatchers(HttpMethod.POST, "/caja/comprobantes/*/anular").hasAuthority(FACTURAS_ANULAR)
                .requestMatchers(HttpMethod.POST, "/caja/comprobantes/*/sincronizar").hasAnyAuthority(FACTURACION_COBRAR, FACTURAS_ANULAR)
                .requestMatchers("/caja/comprobantes/**").hasAuthority(FACTURACION_VER)
                .requestMatchers(HttpMethod.POST, "/caja/cierres/**").hasAuthority(CAJA_CERRAR)
                .requestMatchers("/caja/cierres/**").hasAnyAuthority(CAJA_CERRAR, FACTURACION_VER)
                .requestMatchers("/caja/descuentos/**").hasAuthority(DESCUENTOS_AUTORIZAR)
                .requestMatchers("/caja", "/caja/").hasAnyAuthority(FACTURACION_COBRAR, FACTURACION_VER)
                .anyRequest().authenticated());
        return http.build();
    }
}
