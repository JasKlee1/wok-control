package com.wokcontrol.common.exception;

import org.springframework.http.HttpStatus;

/** Duplicados u operaciones que chocan con el estado actual (409). */
public class ConflictoException extends WokException {

    public ConflictoException(String mensaje) {
        super(HttpStatus.CONFLICT, mensaje);
    }
}
