package com.wokcontrol.menu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
import com.wokcontrol.menu.config.SecurityConfig;
import com.wokcontrol.menu.controller.api.CartaApiRestController;
import com.wokcontrol.menu.controller.api.ImagenApiRestController;
import com.wokcontrol.menu.controller.api.PlatoApiRestController;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.service.CartaService;
import com.wokcontrol.menu.service.ImagenService;
import com.wokcontrol.menu.service.PlatoService;

@WebMvcTest(controllers = { PlatoApiRestController.class, CartaApiRestController.class, ImagenApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class MenuApiSecurityTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    PlatoService platos;
    @MockitoBean
    CartaService carta;
    @MockitoBean
    ImagenService imagenes;

    private String token(String rol) {
        return "Bearer " + tokens.emitirTokenUsuario("u", "U", List.of(rol), Roles.SISTEMA.get(rol).permisos(), null).token();
    }

    private static final String PLATO_VALIDO = "{\"nombre\":\"Arroz frito\",\"categoriaId\":\"c1\",\"precio\":25000,"
            + "\"disponible\":true,\"picanteBase\":1,\"tiempoPreparacionMin\":12}";

    @Test
    void meseroConsultaPlatos() throws Exception {
        Plato p = new Plato();
        p.setId("p1");
        p.setNombre("Arroz frito");
        p.setPrecio(new BigDecimal("25000"));
        p.setCategoriaId("c1");
        when(platos.buscar(any(), any(), any(), anyBoolean())).thenReturn(List.of(p));
        when(platos.nombresCategorias()).thenReturn(new java.util.HashMap<>(Map.of("c1", "Arroces")));
        mvc.perform(get("/api/menu/platos").header("Authorization", token(Roles.MESERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Arroz frito"));
    }

    @Test
    void meseroNoPuedeCrearNiRetirarPlatos() throws Exception {
        mvc.perform(post("/api/menu/platos").header("Authorization", token(Roles.MESERO))
                .contentType(MediaType.APPLICATION_JSON).content(PLATO_VALIDO))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/menu/platos/p1").header("Authorization", token(Roles.MESERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cajeroNoPuedeCambiarPrecios() throws Exception {
        mvc.perform(post("/api/menu/platos").header("Authorization", token(Roles.CAJERO))
                .contentType(MediaType.APPLICATION_JSON).content(PLATO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorCreaPlato() throws Exception {
        Plato p = new Plato();
        p.setId("nuevo");
        p.setNombre("Arroz frito");
        when(platos.crear(any())).thenReturn(p);
        when(platos.nombresCategorias()).thenReturn(new java.util.HashMap<>(Map.of("c1", "Arroces")));
        mvc.perform(post("/api/menu/platos").header("Authorization", token(Roles.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON).content(PLATO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("nuevo"));
    }

    @Test
    void precioNegativoYCamposVaciosSonRechazados() throws Exception {
        mvc.perform(post("/api/menu/platos").header("Authorization", token(Roles.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"categoriaId\":\"\",\"precio\":-5,\"picanteBase\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precio").exists())
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.picanteBase").exists());
    }

    @Test
    void proveedorNoVeLaCarta() throws Exception {
        mvc.perform(get("/api/menu/carta").header("Authorization", token(Roles.PROVEEDOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cotizacionPermitidaATokenDeServicio() throws Exception {
        when(carta.cotizar(any())).thenReturn(new com.wokcontrol.menu.dto.MenuDtos.CotizacionResponse(List.of(), true));
        mvc.perform(post("/api/menu/cotizacion").header("Authorization", "Bearer " + tokens.emitirTokenServicio("pedidos-service", "mesero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"tipo\":\"PLATO\",\"id\":\"p1\",\"cantidad\":2}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void soloQuienGestionaLaCartaPuedeSubirFotos() throws Exception {
        var foto = new org.springframework.mock.web.MockMultipartFile("archivo", "f.jpg", "image/jpeg",
                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 });
        mvc.perform(multipart("/api/menu/platos/p1/imagen").file(foto).with(r -> { r.setMethod("PUT"); return r; })
                .header("Authorization", token(Roles.MESERO)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/menu/platos/p1/imagen").file(foto).with(r -> { r.setMethod("PUT"); return r; })
                .header("Authorization", token(Roles.ADMINISTRADOR)))
                .andExpect(status().isOk());
    }
}
