package com.wokcontrol.usuarios.service;

import org.springframework.http.HttpStatus;

import com.wokcontrol.common.exception.WokException;

public class CredencialesInvalidasException extends WokException {

    public CredencialesInvalidasException(String mensaje) {
        super(HttpStatus.UNAUTHORIZED, mensaje);
    }
}
