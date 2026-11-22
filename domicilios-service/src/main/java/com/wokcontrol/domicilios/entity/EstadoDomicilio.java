package com.wokcontrol.domicilios.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * v2 · CR-001: estados del domicilio. RECIBIDO, EN_PREPARACION y LISTO reflejan el avance en cocina
 * (pedidos-service); los demás los cambia el cajero o el domiciliario.
 *
 * <pre>
 * RECIBIDO → EN_PREPARACION → LISTO → ASIGNADO → EN_CAMINO → ENTREGADO (con cobro y comprobante)
 *                                                          ↘ NO_ENTREGADO (sin comprobante)
 * (antes del despacho) → CANCELADO
 * </pre>
 */
public enum EstadoDomicilio {
    RECIBIDO, EN_PREPARACION, LISTO, ASIGNADO, EN_CAMINO, ENTREGADO, NO_ENTREGADO, CANCELADO;

    /** Antes del despacho: el cajero todavía puede editar o cancelar (HU-047). */
    public static final Set<EstadoDomicilio> ANTES_DEL_DESPACHO = EnumSet.of(RECIBIDO, EN_PREPARACION, LISTO, ASIGNADO);
    /** Todavía en cocina: no se puede asignar (AC-048.2). */
    public static final Set<EstadoDomicilio> EN_COCINA = EnumSet.of(RECIBIDO, EN_PREPARACION);
    public static final Set<EstadoDomicilio> ACTIVOS = EnumSet.of(RECIBIDO, EN_PREPARACION, LISTO, ASIGNADO, EN_CAMINO);
    public static final Set<EstadoDomicilio> FINALES = EnumSet.of(ENTREGADO, NO_ENTREGADO, CANCELADO);
    /** Lo que ve el domiciliario en «Mis entregas» (AC-049.1). */
    public static final Set<EstadoDomicilio> DEL_DOMICILIARIO = EnumSet.of(ASIGNADO, EN_CAMINO);

    public boolean antesDelDespacho() {
        return ANTES_DEL_DESPACHO.contains(this);
    }

    public String etiqueta() {
        return switch (this) {
            case RECIBIDO -> "Recibido (en cocina)";
            case EN_PREPARACION -> "En preparación";
            case LISTO -> "Listo para despachar";
            case ASIGNADO -> "Asignado";
            case EN_CAMINO -> "En camino";
            case ENTREGADO -> "Entregado";
            case NO_ENTREGADO -> "No entregado";
            case CANCELADO -> "Cancelado";
        };
    }

    public String claseCss() {
        return switch (this) {
            case RECIBIDO -> "dorado";
            case EN_PREPARACION -> "alerta";
            case LISTO -> "exito";
            case ASIGNADO -> "info";
            case EN_CAMINO -> "rojo";
            case ENTREGADO -> "neutro";
            case NO_ENTREGADO, CANCELADO -> "error";
        };
    }
}
