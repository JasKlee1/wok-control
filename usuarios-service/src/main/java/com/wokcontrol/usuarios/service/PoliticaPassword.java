package com.wokcontrol.usuarios.service;

import com.wokcontrol.common.exception.ReglaNegocioException;

/** Política mínima de contraseñas: 8 caracteres, al menos una letra y un número. */
public final class PoliticaPassword {

    private PoliticaPassword() {
    }

    public static void validar(String password) {
        if (password == null || password.length() < 8) {
            throw new ReglaNegocioException("La contraseña debe tener al menos 8 caracteres");
        }
        if (!password.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") || !password.matches(".*\\d.*")) {
            throw new ReglaNegocioException("La contraseña debe contener al menos una letra y un número");
        }
    }
}
