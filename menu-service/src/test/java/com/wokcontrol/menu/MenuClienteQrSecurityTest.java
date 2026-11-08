package com.wokcontrol.menu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.menu.config.SecurityConfig;
import com.wokcontrol.menu.controller.api.CartaApiRestController;
import com.wokcontrol.menu.controller.web.ImagenWebController;
import com.wokcontrol.menu.service.CartaService;
import com.wokcontrol.menu.service.ImagenService;

import jakarta.servlet.http.Cookie;

/**
 * Épica 7 · T001 y T003: el cliente con sesión de mesa ve las fotos de la carta, pero no entra a la
 * gestión del menú ni a su API (HU-036, AC-036.1).
 */
@WebMvcTest(controllers = { ImagenWebController.class, CartaApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class MenuClienteQrSecurityTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    ImagenService imagenes;
    @MockitoBean
    CartaService carta;

    private Cookie sesionMesa() {
        return new Cookie("WOK_TOKEN", tokens.emitirTokenMesa("s1", "m5", 5).token());
    }

    @Test
    void clienteVeLasFotosDeLaCarta() throws Exception {
        // La foto no existe en el mock: 404 demuestra que la seguridad dejó pasar la petición.
        mvc.perform(get("/menu/imagenes/img1").cookie(sesionMesa())).andExpect(status().isNotFound());
    }

    @Test
    void clienteNoEntraALaGestionDelMenu() throws Exception {
        mvc.perform(get("/menu/platos").cookie(sesionMesa())).andExpect(redirectedUrl("/acceso-denegado"));
    }

    @Test
    void clienteNoUsaLaApiDelMenu() throws Exception {
        mvc.perform(get("/api/menu/carta").header("Authorization", "Bearer " + tokens.emitirTokenMesa("s1", "m5", 5).token()))
                .andExpect(status().isForbidden());
    }
}
