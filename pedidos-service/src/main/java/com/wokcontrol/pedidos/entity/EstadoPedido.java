package com.wokcontrol.pedidos.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del pedido y transiciones permitidas. Después de enviarlo a cocina, el estado se deriva
 * del avance de sus ítems (ver {@link Pedido#recalcularEstado()}).
 */
public enum EstadoPedido {
    /** v2 · HU-034: pedido hecho por QR que espera la confirmación del mesero (no llega a cocina). */
    POR_CONFIRMAR,
    ABIERTO, ENVIADO, EN_PREPARACION, LISTO, ENTREGADO, PAGADO, CANCELADO,
    /** v2 · HU-037: pedido QR que el mesero rechazó con un motivo. */
    RECHAZADO;

    public static final Set<EstadoPedido> ACTIVOS = EnumSet.of(POR_CONFIRMAR, ABIERTO, ENVIADO, EN_PREPARACION, LISTO, ENTREGADO);
    public static final Set<EstadoPedido> EN_COCINA = EnumSet.of(ENVIADO, EN_PREPARACION, LISTO);

    /** RN-02: antes de cocina el mesero puede corregir el pedido, también el que llegó por QR. */
    public boolean permiteEditarItems() {
        return this == ABIERTO || this == POR_CONFIRMAR;
    }

    /** Solo se cancela antes de que cocina empiece a preparar. */
    public boolean permiteCancelar() {
        return this == ABIERTO || this == ENVIADO;
    }

    public String etiqueta() {
        return switch (this) {
            case POR_CONFIRMAR -> "Por confirmar";
            case ABIERTO -> "Abierto";
            case ENVIADO -> "En cocina";
            case EN_PREPARACION -> "En preparación";
            case LISTO -> "Listo";
            case ENTREGADO -> "Entregado";
            case PAGADO -> "Pagado";
            case CANCELADO -> "Cancelado";
            case RECHAZADO -> "Rechazado";
        };
    }

    public String claseCss() {
        return switch (this) {
            case POR_CONFIRMAR -> "alerta";
            case ABIERTO -> "info";
            case ENVIADO -> "dorado";
            case EN_PREPARACION -> "alerta";
            case LISTO -> "exito";
            case ENTREGADO -> "rojo";
            case PAGADO -> "neutro";
            case CANCELADO -> "error";
            case RECHAZADO -> "error";
        };
    }
}
