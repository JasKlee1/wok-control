package com.wokcontrol.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.FlashMap;

import jakarta.servlet.http.Cookie;

class CookieFlashMapManagerTest {

    private final CookieFlashMapManager manager = new CookieFlashMapManager();

    @Test
    void elMensajeSobreviveAlRedirectYSeConsumeUnaSolaVez() {
        FlashMap fm = new FlashMap();
        fm.put("exito", "Usuario creado · ñandú");
        String valor = CookieFlashMapManager.codificar(fm);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/plataforma/usuarios");
        req.setCookies(new Cookie(CookieFlashMapManager.COOKIE, valor));
        MockHttpServletResponse res = new MockHttpServletResponse();

        FlashMap recuperado = manager.retrieveAndUpdate(req, res);

        assertThat((Object) recuperado).isNotNull();
        assertThat(recuperado.get("exito")).isEqualTo("Usuario creado · ñandú");
        // la cookie se borra tras consumirse
        assertThat(res.getCookie(CookieFlashMapManager.COOKIE).getMaxAge()).isZero();
    }

    @Test
    void cookieAlteradaSeIgnoraSinError() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setCookies(new Cookie(CookieFlashMapManager.COOKIE, "%%%no-es-base64"));
        manager.retrieveAndUpdate(req, new MockHttpServletResponse());
    }
}
