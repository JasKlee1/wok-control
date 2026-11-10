package com.wokcontrol.menu;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.menu.client.InventarioClient;
import com.wokcontrol.menu.config.SecurityConfig;
import com.wokcontrol.menu.controller.web.PlatoWebController;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.service.CategoriaService;
import com.wokcontrol.menu.service.PlatoService;

import jakarta.servlet.http.Cookie;

/**
 * Épica 7 · T011 (HU-038, AC-038.1): el cocinero marca un plato como agotado. Antes solo podía
 * hacerlo quien gestiona el menú; el cocinero sigue sin poder editar platos.
 */
@WebMvcTest(controllers = PlatoWebController.class)
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class CocineroAgotaPlatoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    PlatoService platos;
    @MockitoBean
    CategoriaService categorias;
    @MockitoBean
    InventarioClient inventario;

    private Cookie cocinero() {
        return new Cookie("WOK_TOKEN", tokens.emitirTokenUsuario("cocina1", "Cocinero", List.of(Roles.COCINERO),
                List.of(Permisos.MENU_VER, Permisos.COCINA_OPERAR), null).token());
    }

    @Test
    void ac038_1_elCocineroMarcaUnPlatoAgotado() throws Exception {
        Plato pato = new Plato();
        pato.setNombre("Pato laqueado");
        when(platos.cambiarDisponibilidad("p9", false)).thenReturn(pato);

        mvc.perform(post("/menu/platos/p9/disponibilidad").param("disponible", "false").with(csrf()).cookie(cocinero()))
                .andExpect(redirectedUrl("/menu/platos"));
        verify(platos).cambiarDisponibilidad("p9", false);
    }

    @Test
    void elCocineroNoEditaPlatos() throws Exception {
        mvc.perform(get("/menu/platos/p9/editar").cookie(cocinero())).andExpect(redirectedUrl("/acceso-denegado"));
    }
}
