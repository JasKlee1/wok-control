package com.wokcontrol.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Propaga un identificador de correlación (X-Correlation-Id) para seguir una operación a través de
 * los registros de todos los microservicios.
 * <p>
 * v2 · Sprint 4 · PBI-29 (análisis estático, HRS_REQUEST_PARAMETER_TO_HTTP_HEADER): el valor recibido se
 * copia a la respuesta y a los registros, así que solo se acepta si tiene letras, números, guion, punto o
 * guion bajo (1 a 64). Cualquier otro valor (saltos de línea, espacios, etc.) se reemplaza por un UUID,
 * para evitar la división de respuestas HTTP y la inyección en los registros.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = normalizar(request.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String normalizar(String recibido) {
        return recibido != null && VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
    }
}
