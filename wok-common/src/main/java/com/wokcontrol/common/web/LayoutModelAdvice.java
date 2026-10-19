package com.wokcontrol.common.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.wokcontrol.common.security.UsuarioActual;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Datos comunes a todas las vistas: usuario autenticado, menú lateral filtrado y ruta actual.
 */
@ControllerAdvice(annotations = Controller.class)
public class LayoutModelAdvice {

    @ModelAttribute("usuarioActual")
    UsuarioActual usuarioActual() {
        return UsuarioActual.opcional().orElse(null);
    }

    @ModelAttribute("navegacion")
    List<Navegacion.Seccion> navegacion() {
        return UsuarioActual.opcional().map(Navegacion::para).orElse(List.of());
    }

    @ModelAttribute("rutaActual")
    String rutaActual(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
