package com.wokcontrol.common.exception;

import org.springframework.http.HttpStatus;

/** Falla o indisponibilidad de otro microservicio (503). */
public class ServicioRemotoException extends WokException {

    private final String servicio;

    public ServicioRemotoException(String servicio, String mensaje, Throwable causa) {
        super(HttpStatus.SERVICE_UNAVAILABLE, mensaje, causa);
        this.servicio = servicio;
    }

    public String getServicio() {
        return servicio;
    }
}
