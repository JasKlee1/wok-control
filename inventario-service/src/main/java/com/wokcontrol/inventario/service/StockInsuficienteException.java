package com.wokcontrol.inventario.service;

import java.math.BigDecimal;

import com.wokcontrol.common.exception.ReglaNegocioException;

public class StockInsuficienteException extends ReglaNegocioException {

    public StockInsuficienteException(String insumo, BigDecimal disponible, BigDecimal requerido, String unidad) {
        super("Stock insuficiente de " + insumo + ": disponible " + disponible.stripTrailingZeros().toPlainString() + " "
                + unidad + ", requerido " + requerido.stripTrailingZeros().toPlainString() + " " + unidad);
    }
}
