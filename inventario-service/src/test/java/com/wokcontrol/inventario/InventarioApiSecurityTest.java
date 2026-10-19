package com.wokcontrol.inventario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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

import com.wokcontrol.common.config.PaginacionConfig;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.inventario.config.SecurityConfig;
import com.wokcontrol.inventario.controller.api.InsumoApiRestController;
import com.wokcontrol.inventario.controller.api.InventarioApiRestController;
import com.wokcontrol.inventario.dto.InventarioDtos.ResultadoOperacion;
import com.wokcontrol.inventario.entity.ReporteAgotado;
import com.wokcontrol.inventario.service.InsumoService;
import com.wokcontrol.inventario.service.MovimientoService;
import com.wokcontrol.inventario.service.OperacionInternaService;
import com.wokcontrol.inventario.service.StockInsuficienteException;

@WebMvcTest(controllers = { InsumoApiRestController.class, InventarioApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class, PaginacionConfig.class })
class InventarioApiSecurityTest {

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

    private String token(String rol) {
        return "Bearer " + tokens.emitirTokenUsuario("u-" + rol, "U", List.of(rol), Roles.SISTEMA.get(rol).permisos(), null).token();
    }

    private static final String SALIDA = "{\"tipo\":\"SALIDA\",\"cantidad\":2,\"motivo\":\"Uso interno\"}";
    private static final String CONSUMO = "{\"referencia\":\"PEDIDO-1\",\"lineas\":[{\"insumoId\":\"a\",\"cantidad\":1}]}";

    @Test
    void cocineroPuedeReportarAgotadoPeroNoRegistrarMovimientos() throws Exception {
        when(insumos.reportarAgotado(eq("i1"), any())).thenReturn(new ReporteAgotado("i1", "Pollo", "Se acabó", "cocinero"));
        mvc.perform(post("/api/insumos/i1/reportes-agotado").header("Authorization", token(Roles.COCINERO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacion\":\"Se acabó\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/insumos/i1/movimientos").header("Authorization", token(Roles.COCINERO))
                .contentType(MediaType.APPLICATION_JSON).content(SALIDA))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockInsuficienteResponde422ConMensajeClaro() throws Exception {
        when(movimientos.registrar(eq("i1"), any()))
                .thenThrow(new StockInsuficienteException("Pollo", java.math.BigDecimal.ONE, new java.math.BigDecimal("2"), "KG"));
        mvc.perform(post("/api/insumos/i1/movimientos").header("Authorization", token(Roles.INVENTARIO))
                .contentType(MediaType.APPLICATION_JSON).content(SALIDA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Stock insuficiente de Pollo: disponible 1 KG, requerido 2 KG"));
    }

    @Test
    void cantidadNegativaEsInvalida() throws Exception {
        mvc.perform(post("/api/insumos/i1/movimientos").header("Authorization", token(Roles.INVENTARIO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"ENTRADA\",\"cantidad\":-3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cantidad").exists());
    }

    @Test
    void ajustesSoloConPermisoDeAjuste() throws Exception {
        String ajuste = "{\"cantidadContada\":5,\"motivo\":\"Conteo semanal\"}";
        mvc.perform(post("/api/insumos/i1/ajustes").header("Authorization", token(Roles.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content(ajuste))
                .andExpect(status().isForbidden());
    }

    @Test
    void consumosSoloConTokenDeServicio() throws Exception {
        mvc.perform(post("/api/inventario/consumos").header("Authorization", token(Roles.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content(CONSUMO))
                .andExpect(status().isForbidden());
        when(operaciones.consumir(any())).thenReturn(new ResultadoOperacion("PEDIDO-1", "APLICADO", false, 1));
        mvc.perform(post("/api/inventario/consumos")
                .header("Authorization", "Bearer " + tokens.emitirTokenServicio("pedidos-service", "mesero1"))
                .contentType(MediaType.APPLICATION_JSON).content(CONSUMO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADO"));
    }

    @Test
    void meseroNoConsultaInventarioYProveedorTampoco() throws Exception {
        mvc.perform(get("/api/insumos").header("Authorization", token(Roles.MESERO))).andExpect(status().isForbidden());
        mvc.perform(get("/api/insumos").header("Authorization", token(Roles.PROVEEDOR))).andExpect(status().isForbidden());
    }

    @Test
    void proveedorNoPuedeModificarInventario() throws Exception {
        mvc.perform(post("/api/insumos/i1/movimientos").header("Authorization", token(Roles.PROVEEDOR))
                .contentType(MediaType.APPLICATION_JSON).content(SALIDA))
                .andExpect(status().isForbidden());
    }
}
