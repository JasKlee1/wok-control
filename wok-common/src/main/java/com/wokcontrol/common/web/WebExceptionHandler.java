package com.wokcontrol.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.wokcontrol.common.exception.WokException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Manejo de errores de los controladores web (Thymeleaf): muestra la plantilla de error con un
 * mensaje comprensible. Los errores esperados de formularios se gestionan en cada controlador.
 */
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    String denegado() {
        return "redirect:/acceso-denegado";
    }

    @ExceptionHandler(WokException.class)
    ModelAndView negocio(WokException ex, HttpServletResponse res) {
        return vista(ex.getStatus(), ex.getMessage(), res);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ModelAndView noEncontrado(NoResourceFoundException ex, HttpServletResponse res) {
        return vista(HttpStatus.NOT_FOUND, "La página solicitada no existe", res);
    }

    @ExceptionHandler(Exception.class)
    ModelAndView inesperado(Exception ex, HttpServletResponse res) {
        log.error("Error no controlado en vista web", ex);
        return vista(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intente nuevamente.", res);
    }

    private ModelAndView vista(HttpStatus status, String mensaje, HttpServletResponse res) {
        res.setStatus(status.value());
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(status);
        mv.addObject("status", status.value());
        mv.addObject("error", status.getReasonPhrase());
        mv.addObject("message", mensaje);
        return mv;
    }
}
