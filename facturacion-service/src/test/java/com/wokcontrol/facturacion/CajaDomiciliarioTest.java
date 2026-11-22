package com.wokcontrol.facturacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.facturacion.client.PedidosClient;
import com.wokcontrol.facturacion.config.SecurityConfig;
import com.wokcontrol.facturacion.controller.api.CajaApiRestController;
import com.wokcontrol.facturacion.controller.api.FacturaApiRestController;
import com.wokcontrol.facturacion.controller.web.CajaWebController;
import com.wokcontrol.facturacion.service.CierreCajaService;
import com.wokcontrol.facturacion.service.DescuentoService;
import com.wokcontrol.facturacion.service.FacturacionService;
import com.wokcontrol.facturacion.service.ReporteVentasService;

import jakarta.servlet.http.Cookie;

/**
 * v2 · Sprint 3 · HU-051 (AC-051.2, RN-15): el domiciliario no abre la caja ni cobra por la API; ve el mensaje
 * de acceso no permitido. El cobro de domicilios solo entra con token de servicio.
 */
@WebMvcTest(controllers = { CajaWebController.class, FacturaApiRestController.class, CajaApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class CajaDomiciliarioTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    FacturacionService facturacion;
    @MockitoBean
    ReporteVentasService reportes;
    @MockitoBean
    CierreCajaService cierres;
    @MockitoBean
    DescuentoService descuentos;
    @MockitoBean
    PedidosClient pedidos;

    private String token() {
        return tokens.emitirTokenUsuario("luis", "Luis", List.of(Roles.DOMICILIARIO),
                Roles.SISTEMA.get(Roles.DOMICILIARIO).permisos(), null).token();
    }

    @Test
    void ac051_2_elDomiciliarioAbreCajaYVeAccesoNoPermitido() throws Exception {
        Cookie c = new Cookie("WOK_TOKEN", token());
        mvc.perform(get("/caja/cuentas").cookie(c)).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/caja/cuentas/m3").cookie(c)).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/caja/comprobantes").cookie(c)).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/caja/cierres").cookie(c)).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(post("/caja/cuentas/m3/cobrar").with(csrf()).cookie(c)).andExpect(redirectedUrl("/acceso-denegado"));
    }

    @Test
    void ac051_2_elDomiciliarioNoCobraPorLaApiNiFacturaDomicilios() throws Exception {
        String bearer = "Bearer " + token();
        mvc.perform(get("/api/cuentas/m3").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/facturas").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":\"m3\",\"pagos\":[{\"metodo\":\"EFECTIVO\",\"monto\":1000}],\"claveIdempotencia\":\"abcdefgh-1234\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/facturas/domicilios").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        verify(facturacion, never()).cobrar(any());
        verify(facturacion, never()).cobrarDomicilio(any());
    }
}
