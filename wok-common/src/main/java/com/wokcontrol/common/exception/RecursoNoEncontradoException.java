package com.wokcontrol.common.exception;

import org.springframework.http.HttpStatus;

public class RecursoNoEncontradoException extends WokException {

    public RecursoNoEncontradoException(String recurso, String id) {
        super(HttpStatus.NOT_FOUND, recurso + " no encontrado: " + id);
    }

    public RecursoNoEncontradoException(String mensaje) {
        super(HttpStatus.NOT_FOUND, mensaje);
    }
}
