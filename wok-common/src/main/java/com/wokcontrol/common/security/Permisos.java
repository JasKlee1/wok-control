package com.wokcontrol.common.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catálogo de permisos del sistema. Los roles (ver {@link Roles}) son conjuntos de estos permisos
 * y la autorización en el backend se evalúa siempre por permiso, nunca por lo que muestre la interfaz.
 */
public final class Permisos {

    public static final String DASHBOARD_VER = "DASHBOARD_VER";
    public static final String REPORTES_VER = "REPORTES_VER";

    public static final String USUARIOS_GESTIONAR = "USUARIOS_GESTIONAR";
    public static final String ROLES_GESTIONAR = "ROLES_GESTIONAR";
    public static final String AUDITORIA_VER = "AUDITORIA_VER";
    public static final String PARAMETROS_GESTIONAR = "PARAMETROS_GESTIONAR";

    public static final String MENU_VER = "MENU_VER";
    public static final String MENU_GESTIONAR = "MENU_GESTIONAR";

    public static final String MESAS_VER = "MESAS_VER";
    public static final String MESAS_GESTIONAR = "MESAS_GESTIONAR";
    public static final String PEDIDOS_VER = "PEDIDOS_VER";
    public static final String PEDIDOS_GESTIONAR = "PEDIDOS_GESTIONAR";
    public static final String COCINA_OPERAR = "COCINA_OPERAR";

    public static final String INVENTARIO_VER = "INVENTARIO_VER";
    public static final String INVENTARIO_GESTIONAR = "INVENTARIO_GESTIONAR";
    public static final String INVENTARIO_MOVIMIENTOS = "INVENTARIO_MOVIMIENTOS";
    public static final String INVENTARIO_AJUSTAR = "INVENTARIO_AJUSTAR";
    public static final String INSUMOS_REPORTAR_AGOTADO = "INSUMOS_REPORTAR_AGOTADO";

    public static final String PROVEEDORES_VER = "PROVEEDORES_VER";
    public static final String PROVEEDORES_GESTIONAR = "PROVEEDORES_GESTIONAR";
    public static final String COMPRAS_GESTIONAR = "COMPRAS_GESTIONAR";
    public static final String PORTAL_PROVEEDOR = "PORTAL_PROVEEDOR";

    public static final String FACTURACION_VER = "FACTURACION_VER";
    public static final String FACTURACION_COBRAR = "FACTURACION_COBRAR";
    public static final String DESCUENTOS_AUTORIZAR = "DESCUENTOS_AUTORIZAR";
    public static final String FACTURAS_ANULAR = "FACTURAS_ANULAR";
    public static final String CAJA_CERRAR = "CAJA_CERRAR";

    /** Permiso reservado a los tokens internos emitidos entre microservicios. */
    public static final String SISTEMA = "SISTEMA";

    public static final Map<String, String> DESCRIPCIONES;

    static {
        Map<String, String> d = new LinkedHashMap<>();
        d.put(DASHBOARD_VER, "Consultar el panel de control");
        d.put(REPORTES_VER, "Consultar reportes de ventas, costos y operación");
        d.put(USUARIOS_GESTIONAR, "Crear, editar, activar y desactivar usuarios");
        d.put(ROLES_GESTIONAR, "Crear y editar roles personalizados");
        d.put(AUDITORIA_VER, "Consultar la auditoría de operaciones");
        d.put(PARAMETROS_GESTIONAR, "Administrar parámetros generales de la plataforma");
        d.put(MENU_VER, "Consultar la carta");
        d.put(MENU_GESTIONAR, "Gestionar categorías, platos, combos, recetas y precios");
        d.put(MESAS_VER, "Consultar mesas y su ocupación");
        d.put(MESAS_GESTIONAR, "Crear y editar mesas");
        d.put(PEDIDOS_VER, "Consultar pedidos y comandas");
        d.put(PEDIDOS_GESTIONAR, "Registrar, modificar, enviar y cancelar pedidos");
        d.put(COCINA_OPERAR, "Cambiar estados de preparación en cocina");
        d.put(INVENTARIO_VER, "Consultar existencias, movimientos y alertas");
        d.put(INVENTARIO_GESTIONAR, "Crear y editar insumos");
        d.put(INVENTARIO_MOVIMIENTOS, "Registrar entradas, salidas y mermas");
        d.put(INVENTARIO_AJUSTAR, "Registrar ajustes auditados de existencias");
        d.put(INSUMOS_REPORTAR_AGOTADO, "Reportar insumos agotados desde cocina");
        d.put(PROVEEDORES_VER, "Consultar proveedores");
        d.put(PROVEEDORES_GESTIONAR, "Crear y editar proveedores");
        d.put(COMPRAS_GESTIONAR, "Gestionar órdenes de compra y recepción de mercancía");
        d.put(PORTAL_PROVEEDOR, "Acceso del proveedor a su perfil y sus órdenes");
        d.put(FACTURACION_VER, "Consultar cuentas, comprobantes y cierres");
        d.put(FACTURACION_COBRAR, "Registrar pagos y emitir comprobantes internos");
        d.put(DESCUENTOS_AUTORIZAR, "Autorizar descuentos");
        d.put(FACTURAS_ANULAR, "Anular comprobantes con motivo");
        d.put(CAJA_CERRAR, "Realizar cierres de caja");
        DESCRIPCIONES = Map.copyOf(d);
    }

    private Permisos() {
    }

    public static boolean existe(String permiso) {
        return DESCRIPCIONES.containsKey(permiso);
    }
}
