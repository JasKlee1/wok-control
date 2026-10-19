package com.wokcontrol.proveedores;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.wokcontrol.proveedores.config.SecurityConfig;
import com.wokcontrol.proveedores.controller.api.OrdenCompraApiRestController;
import com.wokcontrol.proveedores.controller.api.ProveedorApiRestController;
import com.wokcontrol.proveedores.entity.Proveedor;
import com.wokcontrol.proveedores.service.OrdenCompraService;
import com.wokcontrol.proveedores.service.ProveedorService;

@WebMvcTest(controllers = { ProveedorApiRestController.class, OrdenCompraApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class ProveedoresApiSecurityTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    ProveedorService proveedores;
    @MockitoBean
    OrdenCompraService ordenes;

    private String token(String rol, String proveedorId) {
        return "Bearer " + tokens.emitirTokenUsuario("u", "U", List.of(rol), Roles.SISTEMA.get(rol).permisos(), proveedorId).token();
    }

    @Test
    void proveedorNoListaOtrosProveedoresNiCreaOrdenes() throws Exception {
        mvc.perform(get("/api/proveedores").header("Authorization", token(Roles.PROVEEDOR, "p1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ordenes-compra").header("Authorization", token(Roles.PROVEEDOR, "p1"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/ordenes-compra/oc1/recepcion").header("Authorization", token(Roles.PROVEEDOR, "p1"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"lineas\":[{\"insumoId\":\"a\",\"cantidadRecibida\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void proveedorConsultaSuPerfil() throws Exception {
        Proveedor p = new Proveedor();
        p.setId("p1");
        p.setNombreComercial("Oriente");
        when(proveedores.miPerfil()).thenReturn(p);
        mvc.perform(get("/api/proveedores/mi-perfil").header("Authorization", token(Roles.PROVEEDOR, "p1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreComercial").value("Oriente"));
    }

    @Test
    void personalInternoNoUsaElPortalDeProveedor() throws Exception {
        mvc.perform(get("/api/ordenes-compra/mias").header("Authorization", token(Roles.ADMINISTRADOR, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validacionDeNitTelefonoYCorreo() throws Exception {
        mvc.perform(post("/api/proveedores").header("Authorization", token(Roles.ADMINISTRADOR, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nit\":\"ABC\",\"razonSocial\":\"X\",\"nombreComercial\":\"X\",\"telefono\":\"12\",\"email\":\"malo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nit").exists())
                .andExpect(jsonPath("$.errores.telefono").exists())
                .andExpect(jsonPath("$.errores.email").exists());
    }

    @Test
    void inventarioConsultaProveedoresPeroNoLosEdita() throws Exception {
        when(proveedores.buscar(null, null, null, null)).thenReturn(List.of());
        mvc.perform(get("/api/proveedores").header("Authorization", token(Roles.INVENTARIO, null))).andExpect(status().isOk());
        mvc.perform(put("/api/proveedores/p1").header("Authorization", token(Roles.INVENTARIO, null))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
}
