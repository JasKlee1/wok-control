package com.wokcontrol.domicilios.service;

import com.wokcontrol.common.exception.ReglaNegocioException;

/** AC-047.2: dato del domicilio inválido; indica el campo para mostrarlo junto al formulario. */
public class CampoInvalidoException extends ReglaNegocioException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensaje) {
        super("CAMPO_INVALIDO", mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
