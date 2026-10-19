package com.wokcontrol.common.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.DispatcherServlet;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import com.wokcontrol.common.web.CookieFlashMapManager;
import com.wokcontrol.common.web.CorrelationIdFilter;

/**
 * Infraestructura compartida: cliente HTTP con balanceo de carga (Eureka) y timeouts explícitos,
 * mensajes flash sin sesión y tareas programadas.
 */
@Configuration
@EnableScheduling
public class WokCommonConfig {

    /**
     * Builder de RestClient resuelto por nombre de servicio en Eureka (http://menu-service/...).
     * Cada cliente debe usar {@code clone()} antes de configurarlo, porque el builder es mutable.
     * {@code defaultCandidate = false}: solo se inyecta con su calificador; así el cliente de Eureka
     * no lo toma para registrarse (lo que crearía una dependencia circular con el balanceador).
     */
    @Bean(defaultCandidate = false)
    @LoadBalanced
    RestClient.Builder restClientBuilderInterno(
            @Value("${wok.http.connect-timeout:3s}") Duration connectTimeout,
            @Value("${wok.http.read-timeout:20s}") Duration readTimeout,
            ObjectProvider<JsonMapper> jsonMapper) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        // Usa el JsonMapper de Spring Boot (spring.jackson.*): ignora propiedades desconocidas y
        // tolera primitivos ausentes, de modo que un servicio puede agregar campos sin romper a otros.
        JsonMapper mapper = jsonMapper.getIfAvailable(() -> JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build());
        return RestClient.builder()
                .requestFactory(factory)
                .configureMessageConverters(c -> c.registerDefaults().withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)))
                .requestInterceptor((request, body, execution) -> {
                    String correlacion = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlacion != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlacion);
                    }
                    return execution.execute(request, body);
                });
    }

    @Bean(name = DispatcherServlet.FLASH_MAP_MANAGER_BEAN_NAME)
    FlashMapManager flashMapManager() {
        return new CookieFlashMapManager();
    }
}
