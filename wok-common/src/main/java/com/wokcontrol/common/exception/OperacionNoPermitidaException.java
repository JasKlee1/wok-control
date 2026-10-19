package com.wokcontrol.common.exception;

import org.springframework.http.HttpStatus;

/** El usuario está autenticado pero no puede actuar sobre este recurso concreto (403). */
public class OperacionNoPermitidaException extends WokException {

    public OperacionNoPermitidaException(String mensaje) {
        super(HttpStatus.FORBIDDEN, mensaje);
    }
}
