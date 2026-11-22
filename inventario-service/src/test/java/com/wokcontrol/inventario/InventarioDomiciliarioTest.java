package com.wokcontrol.inventario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.config.PaginacionConfig;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.inventario.config.SecurityConfig;
import com.wokcontrol.inventario.controller.api.InsumoApiRestController;
import com.wokcontrol.inventario.controller.api.InventarioApiRestController;
import com.wokcontrol.inventario.service.InsumoService;
import com.wokcontrol.inventario.service.MovimientoService;
import com.wokcontrol.inventario.service.OperacionInternaService;

/** v2 · Sprint 3 · HU-051 (AC-051.2, RN-15): el domiciliario no consulta ni mueve el inventario. */
@WebMvcTest(controllers = { InsumoApiRestController.class, InventarioApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class, PaginacionConfig.class })
class InventarioDomiciliarioTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    InsumoService insumos;
    @MockitoBean
    MovimientoService movimientos;
    @MockitoBean
    OperacionInternaService operaciones;

    @Test
    void ac051_2_elDomiciliarioNoEntraAlInventario() throws Exception {
        String t = "Bearer " + tokens.emitirTokenUsuario("luis", "Luis", List.of(Roles.DOMICILIARIO),
                Roles.SISTEMA.get(Roles.DOMICILIARIO).permisos(), null).token();
        mvc.perform(get("/api/insumos").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(post("/api/insumos/i1/movimientos").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"SALIDA\",\"cantidad\":2,\"motivo\":\"Uso interno\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/insumos/i1/reportes-agotado").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"observacion\":\"x\"}")).andExpect(status().isForbidden());
    }
}
