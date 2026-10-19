package com.wokcontrol.common.web;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.wokcontrol.common.exception.WokException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Respuestas de error uniformes (RFC 9457 ProblemDetail) para todos los controladores REST.
 * Solo aplica a {@code @RestController}: los controladores web tienen su propio manejador.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(WokException.class)
    ProblemDetail negocio(WokException ex, HttpServletRequest req) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("Error {} en {}: {}", ex.getStatus().value(), req.getRequestURI(), ex.getMessage());
        }
        return problema(ex.getStatus(), ex.getStatus().getReasonPhrase(), ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge -> errores.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "La solicitud tiene campos inválidos", req);
        pd.setProperty("errores", errores);
        return pd;
    }

    @ExceptionHandler({ ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            IllegalArgumentException.class })
    ProblemDetail parametros(Exception ex, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", ex.getMessage(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail ilegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "Cuerpo inválido",
                "El cuerpo de la solicitud no es un JSON válido o tiene valores con formato incorrecto", req);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicado(DuplicateKeyException ex, HttpServletRequest req) {
        return problema(HttpStatus.CONFLICT, "Registro duplicado", "Ya existe un registro con ese valor único", req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrencia(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return problema(HttpStatus.CONFLICT, "Modificación concurrente",
                "El registro fue modificado por otro usuario. Consulte de nuevo e intente otra vez", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denegado(AccessDeniedException ex, HttpServletRequest req) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "Su rol no tiene permiso para esta operación", req);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Consulte los registros del servicio", req);
    }

    static ProblemDetail problema(HttpStatus status, String titulo, String detalle, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setTitle(titulo);
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
