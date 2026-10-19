package com.wokcontrol.common.exception;

import org.springframework.http.HttpStatus;

/** Violación de una regla de negocio: transición inválida, stock insuficiente, etc. (422). */
public class ReglaNegocioException extends WokException {

    public ReglaNegocioException(String mensaje) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, mensaje);
    }
}
