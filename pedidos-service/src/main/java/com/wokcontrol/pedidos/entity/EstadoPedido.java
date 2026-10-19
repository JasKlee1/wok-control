package com.wokcontrol.pedidos.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del pedido y transiciones permitidas. Después de enviarlo a cocina, el estado se deriva
 * del avance de sus ítems (ver {@link Pedido#recalcularEstado()}).
 */
public enum EstadoPedido {
    ABIERTO, ENVIADO, EN_PREPARACION, LISTO, ENTREGADO, PAGADO, CANCELADO;

    public static final Set<EstadoPedido> ACTIVOS = EnumSet.of(ABIERTO, ENVIADO, EN_PREPARACION, LISTO, ENTREGADO);
    public static final Set<EstadoPedido> EN_COCINA = EnumSet.of(ENVIADO, EN_PREPARACION, LISTO);

    public boolean esActivo() {
        return ACTIVOS.contains(this);
    }

    public boolean permiteEditarItems() {
        return this == ABIERTO;
    }

    /** Solo se cancela antes de que cocina empiece a preparar. */
    public boolean permiteCancelar() {
        return this == ABIERTO || this == ENVIADO;
    }

    public String etiqueta() {
        return switch (this) {
            case ABIERTO -> "Abierto";
            case ENVIADO -> "En cocina";
            case EN_PREPARACION -> "En preparación";
            case LISTO -> "Listo";
            case ENTREGADO -> "Entregado";
            case PAGADO -> "Pagado";
            case CANCELADO -> "Cancelado";
        };
    }

    public String claseCss() {
        return switch (this) {
            case ABIERTO -> "info";
            case ENVIADO -> "dorado";
            case EN_PREPARACION -> "alerta";
            case LISTO -> "exito";
            case ENTREGADO -> "rojo";
            case PAGADO -> "neutro";
            case CANCELADO -> "error";
        };
    }
}
