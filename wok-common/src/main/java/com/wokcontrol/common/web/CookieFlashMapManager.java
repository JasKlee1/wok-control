package com.wokcontrol.common.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.AbstractFlashMapManager;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guarda los mensajes flash (éxito o error tras un redirect) en una cookie de vida corta en lugar
 * de la sesión HTTP. Los servicios son sin estado y comparten el mismo origen a través del gateway,
 * así que un mensaje generado en un servicio puede mostrarse en la página de otro.
 * Solo admite valores de texto: los formularios con errores se vuelven a mostrar sin redirect.
 */
public class CookieFlashMapManager extends AbstractFlashMapManager {

    static final String COOKIE = "WOK_FLASH";

    @Override
    protected List<FlashMap> retrieveFlashMaps(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                // lista mutable: Spring elimina de ella los mensajes vencidos o ya consumidos
                return new java.util.ArrayList<>(List.of(decodificar(c.getValue())));
            }
        }
        return null;
    }

    @Override
    protected void updateFlashMaps(List<FlashMap> flashMaps, HttpServletRequest request, HttpServletResponse response) {
        Cookie c;
        if (flashMaps.isEmpty()) {
            c = new Cookie(COOKIE, "");
            c.setMaxAge(0);
        } else {
            c = new Cookie(COOKIE, codificar(flashMaps.get(flashMaps.size() - 1)));
            c.setMaxAge(60);
        }
        c.setPath("/");
        c.setHttpOnly(true);
        c.setAttribute("SameSite", "Lax");
        response.addCookie(c);
    }

    static String codificar(FlashMap fm) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : fm.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static FlashMap decodificar(String valor) {
        FlashMap fm = new FlashMap();
        try {
            String plano = new String(Base64.getUrlDecoder().decode(valor), StandardCharsets.UTF_8);
            for (String par : plano.split("&")) {
                int i = par.indexOf('=');
                if (i > 0) {
                    fm.put(URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8),
                            URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        } catch (IllegalArgumentException ex) {
            // cookie alterada o corrupta: se ignora
        }
        fm.startExpirationPeriod(60);
        return fm;
    }
}
