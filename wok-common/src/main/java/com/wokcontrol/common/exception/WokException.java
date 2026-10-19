package com.wokcontrol.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción base de negocio. Cada subclase define el código HTTP con el que se responde.
 */
public abstract class WokException extends RuntimeException {

    private final HttpStatus status;

    protected WokException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    protected WokException(HttpStatus status, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
