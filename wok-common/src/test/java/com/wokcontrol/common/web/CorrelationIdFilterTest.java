package com.wokcontrol.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** v2 · Sprint 4 · PBI-29: hallazgo HRS_REQUEST_PARAMETER_TO_HTTP_HEADER del análisis estático. */
class CorrelationIdFilterTest {

    private String responder(String recibido) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/menu");
        if (recibido != null) {
            req.addHeader(CorrelationIdFilter.HEADER, recibido);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(req, res, new MockFilterChain());
        return res.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void unIdValidoSePropagaIgual() throws Exception {
        assertThat(responder("pedido-123_A.b")).isEqualTo("pedido-123_A.b");
    }

    @Test
    void unIdConSaltosDeLineaOCaracteresRarosSeReemplaza() throws Exception {
        assertThat(responder("abc\r\nSet-Cookie: x=1")).doesNotContain("\r", "\n", "Set-Cookie").hasSize(36);
        assertThat(responder("hola mundo")).isNotEqualTo("hola mundo").hasSize(36);
        assertThat(responder("x".repeat(65))).hasSize(36);
        assertThat(responder(null)).hasSize(36);
    }
}
