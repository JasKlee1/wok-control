package com.wokcontrol.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Violación de una regla de negocio: transición inválida, stock insuficiente, etc. (422).
 * v2 · HU-042/044/045: puede llevar un código estable (p. ej. MESA_NO_DISPONIBLE) que la API
 * devuelve en la propiedad {@code codigo} del ProblemDetail.
 */
public class ReglaNegocioException extends WokException {

    private final String codigo;

    public ReglaNegocioException(String mensaje) {
        this(null, mensaje);
    }

    public ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
