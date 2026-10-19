package com.wokcontrol.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Documento OpenAPI de cada servicio con esquema de seguridad JWT Bearer. El servidor es relativo
 * ("/"), así Swagger UI funciona tanto en el puerto propio del servicio como a través del gateway.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    OpenAPI wokOpenApi(@Value("${wok.openapi.titulo:Wok Control API}") String titulo,
            @Value("${wok.openapi.descripcion:}") String descripcion,
            @Value("${wok.openapi.version:1.0.0}") String version) {
        return new OpenAPI()
                .info(new Info().title(titulo).description(descripcion).version(version)
                        .contact(new Contact().name("Equipo Wok Control")))
                .servers(List.of(new Server().url("/").description("Origen actual (gateway o servicio)")))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Obtenga el token con POST /api/auth/login y péguelo aquí (sin el prefijo Bearer)")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
