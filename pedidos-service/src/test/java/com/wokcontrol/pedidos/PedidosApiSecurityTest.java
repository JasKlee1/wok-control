package com.wokcontrol.pedidos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.wokcontrol.pedidos.config.SecurityConfig;
import com.wokcontrol.pedidos.controller.api.MesaApiRestController;
import com.wokcontrol.pedidos.controller.api.PedidoApiRestController;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;
import com.wokcontrol.pedidos.service.ResumenService;

@WebMvcTest(controllers = { PedidoApiRestController.class, MesaApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class PedidosApiSecurityTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    PedidoService pedidos;
    @MockitoBean
    MesaService mesas;
    @MockitoBean
    ResumenService resumen;

    private String token(String rol) {
        return "Bearer " + tokens.emitirTokenUsuario("u", "U", List.of(rol), Roles.SISTEMA.get(rol).permisos(), null).token();
    }

    @Test
    void pedidoSinProductosEsRechazado() throws Exception {
        mvc.perform(post("/api/pedidos").header("Authorization", token(Roles.MESERO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"mesaId\":\"m1\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.items").value("El pedido debe tener al menos un producto"));
    }

    @Test
    void cantidadesYPicanteSeValidan() throws Exception {
        mvc.perform(post("/api/pedidos").header("Authorization", token(Roles.MESERO))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":\"m1\",\"items\":[{\"tipo\":\"PLATO\",\"productoId\":\"p\",\"cantidad\":0,\"nivelPicante\":9}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['items[0].cantidad']").exists())
                .andExpect(jsonPath("$.errores['items[0].nivelPicante']").exists());
    }

    @Test
    void cocineroNoRegistraPedidosNiCajeroCambiaEstados() throws Exception {
        String cuerpo = "{\"mesaId\":\"m1\",\"items\":[{\"tipo\":\"PLATO\",\"productoId\":\"p\",\"cantidad\":1}]}";
        mvc.perform(post("/api/pedidos").header("Authorization", token(Roles.COCINERO))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/pedidos/p1/estado").header("Authorization", token(Roles.CAJERO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENVIADO\"}")).andExpect(status().isForbidden());
    }

    @Test
    void cocineroConsultaComandas() throws Exception {
        when(pedidos.comandasCocina()).thenReturn(List.of());
        mvc.perform(get("/api/cocina/comandas").header("Authorization", token(Roles.COCINERO))).andExpect(status().isOk());
        mvc.perform(get("/api/cocina/comandas").header("Authorization", token(Roles.PROVEEDOR))).andExpect(status().isForbidden());
    }

    @Test
    void marcarPagadoSoloConTokenDeServicio() throws Exception {
        mvc.perform(post("/api/pedidos/p1/pago").header("Authorization", token(Roles.CAJERO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"facturaId\":\"F1\"}")).andExpect(status().isForbidden());
        Pedido p = new Pedido();
        p.setId("p1");
        when(pedidos.marcarPagado(anyString(), anyString())).thenReturn(p);
        mvc.perform(post("/api/pedidos/p1/pago").header("Authorization", "Bearer " + tokens.emitirTokenServicio("facturacion-service", "cajero1"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"facturaId\":\"F1\"}")).andExpect(status().isOk());
    }

    @Test
    void estadoPagadoNoSePuedeForzarDesdeLaApi() throws Exception {
        mvc.perform(patch("/api/pedidos/p1/estado").header("Authorization", token(Roles.MESERO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PAGADO\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void soloAdministracionCreaMesas() throws Exception {
        String mesa = "{\"numero\":5,\"capacidad\":4,\"zona\":\"SALON\",\"activa\":true}";
        mvc.perform(post("/api/mesas").header("Authorization", token(Roles.MESERO))
                .contentType(MediaType.APPLICATION_JSON).content(mesa)).andExpect(status().isForbidden());
        when(mesas.crear(any())).thenThrow(new com.wokcontrol.common.exception.ConflictoException("Ya existe la mesa 5"));
        mvc.perform(post("/api/mesas").header("Authorization", token(Roles.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content(mesa)).andExpect(status().isConflict());
    }
}
