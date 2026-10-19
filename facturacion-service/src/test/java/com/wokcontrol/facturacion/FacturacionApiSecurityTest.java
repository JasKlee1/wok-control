package com.wokcontrol.facturacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.wokcontrol.facturacion.config.SecurityConfig;
import com.wokcontrol.facturacion.controller.api.CajaApiRestController;
import com.wokcontrol.facturacion.controller.api.FacturaApiRestController;
import com.wokcontrol.facturacion.service.CierreCajaService;
import com.wokcontrol.facturacion.service.DescuentoService;
import com.wokcontrol.facturacion.service.FacturacionService;
import com.wokcontrol.facturacion.service.ReporteVentasService;

@WebMvcTest(controllers = { FacturaApiRestController.class, CajaApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class FacturacionApiSecurityTest {

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

    private String token(String rol) {
        return "Bearer " + tokens.emitirTokenUsuario("u", "U", List.of(rol), Roles.SISTEMA.get(rol).permisos(), null).token();
    }

    private static final String COBRO = "{\"mesaId\":\"m1\",\"pagos\":[{\"metodo\":\"EFECTIVO\",\"monto\":1000}],\"claveIdempotencia\":\"abcdefgh-1234\"}";

    @Test
    void soloElCajeroCobra() throws Exception {
        mvc.perform(post("/api/facturas").header("Authorization", token(Roles.MESERO))
                .contentType(MediaType.APPLICATION_JSON).content(COBRO)).andExpect(status().isForbidden());
        mvc.perform(post("/api/facturas").header("Authorization", token(Roles.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content(COBRO)).andExpect(status().isForbidden());
    }

    @Test
    void elCuerpoDelCobroSeValidaYNoAceptaTotalesDelCliente() throws Exception {
        mvc.perform(post("/api/facturas").header("Authorization", token(Roles.CAJERO))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":\"\",\"pagos\":[{\"metodo\":\"EFECTIVO\",\"monto\":-5}],\"total\":1,\"claveIdempotencia\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.mesaId").exists())
                .andExpect(jsonPath("$.errores['pagos[0].monto']").exists())
                .andExpect(jsonPath("$.errores.claveIdempotencia").exists());
    }

    @Test
    void cajeroNoAutorizaDescuentosNiAnula() throws Exception {
        mvc.perform(post("/api/descuentos/autorizaciones").header("Authorization", token(Roles.CAJERO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"porcentajeMaximo\":10,\"motivo\":\"Cliente VIP\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/facturas/f1/anulacion").header("Authorization", token(Roles.CAJERO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error de cobro\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorConsultaVentasYCierresPeroNoCierraCaja() throws Exception {
        mvc.perform(get("/api/facturas").header("Authorization", token(Roles.ADMINISTRADOR))).andExpect(status().isOk());
        mvc.perform(get("/api/caja/cierres").header("Authorization", token(Roles.ADMINISTRADOR))).andExpect(status().isOk());
        mvc.perform(post("/api/caja/cierres").header("Authorization", token(Roles.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"declarado\":{}}")).andExpect(status().isForbidden());
    }

    @Test
    void proveedorYCocineroNoVenFacturacion() throws Exception {
        mvc.perform(get("/api/facturas").header("Authorization", token(Roles.PROVEEDOR))).andExpect(status().isForbidden());
        mvc.perform(get("/api/cuentas/mesa/m1").header("Authorization", token(Roles.COCINERO))).andExpect(status().isForbidden());
    }
}
