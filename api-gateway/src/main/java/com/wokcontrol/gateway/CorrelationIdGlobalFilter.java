package com.wokcontrol.gateway;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Asigna un X-Correlation-Id a cada solicitud entrante y registra método, ruta, estado y duración.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (id == null || id.isBlank() || id.length() > 64) {
            id = UUID.randomUUID().toString();
        }
        final String correlacion = id;
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, correlacion).build();
        exchange.getResponse().getHeaders().set(HEADER, correlacion);
        long inicio = System.currentTimeMillis();
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(s -> {
                    String ruta = request.getPath().value();
                    if (ruta.startsWith("/api/")) {
                        log.info("{} {} -> {} ({} ms) [{}]", request.getMethod(), ruta,
                                exchange.getResponse().getStatusCode(), System.currentTimeMillis() - inicio, correlacion);
                    }
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
