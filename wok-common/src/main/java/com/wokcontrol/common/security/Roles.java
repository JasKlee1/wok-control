package com.wokcontrol.common.security;

import static com.wokcontrol.common.security.Permisos.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Roles del sistema y su matriz de permisos. Es la fuente única de verdad: usuarios-service la
 * sincroniza en la base de datos al iniciar y los demás servicios autorizan por los permisos que
 * viajan firmados en el JWT.
 *
 * <p>ADMINISTRADOR es el administrador operativo del restaurante; ADMIN_PLATAFORMA administra
 * usuarios, roles, parámetros y auditoría, pero no opera el restaurante.</p>
 */
public final class Roles {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String MESERO = "MESERO";
    public static final String COCINERO = "COCINERO";
    public static final String CAJERO = "CAJERO";
    public static final String PROVEEDOR = "PROVEEDOR";
    public static final String INVENTARIO = "INVENTARIO";
    public static final String ADMIN_PLATAFORMA = "ADMIN_PLATAFORMA";

    public record DefinicionRol(String codigo, String nombre, String descripcion, Set<String> permisos) {
    }

    public static final Map<String, DefinicionRol> SISTEMA;

    static {
        Map<String, DefinicionRol> r = new LinkedHashMap<>();
        r.put(ADMINISTRADOR, new DefinicionRol(ADMINISTRADOR, "Administrador",
                "Administrador operativo del restaurante: carta, ventas, costos, inventario y proveedores",
                Set.of(DASHBOARD_VER, REPORTES_VER, MENU_VER, MENU_GESTIONAR, MESAS_VER, MESAS_GESTIONAR,
                        PEDIDOS_VER, INVENTARIO_VER, INVENTARIO_GESTIONAR, PROVEEDORES_VER, PROVEEDORES_GESTIONAR,
                        COMPRAS_GESTIONAR, FACTURACION_VER, DESCUENTOS_AUTORIZAR, FACTURAS_ANULAR)));
        r.put(MESERO, new DefinicionRol(MESERO, "Mesero",
                "Atiende mesas, registra pedidos y consulta el estado de las comandas",
                Set.of(DASHBOARD_VER, MENU_VER, MESAS_VER, PEDIDOS_VER, PEDIDOS_GESTIONAR)));
        r.put(COCINERO, new DefinicionRol(COCINERO, "Cocinero",
                "Prepara las comandas, actualiza su estado y reporta insumos agotados",
                Set.of(DASHBOARD_VER, MENU_VER, PEDIDOS_VER, COCINA_OPERAR, INVENTARIO_VER, INSUMOS_REPORTAR_AGOTADO)));
        r.put(CAJERO, new DefinicionRol(CAJERO, "Cajero",
                "Cobra cuentas, aplica descuentos autorizados, emite comprobantes y cierra caja",
                Set.of(DASHBOARD_VER, MENU_VER, MESAS_VER, PEDIDOS_VER, FACTURACION_VER, FACTURACION_COBRAR, CAJA_CERRAR)));
        r.put(PROVEEDOR, new DefinicionRol(PROVEEDOR, "Proveedor",
                "Proveedor externo: consulta su perfil, sus órdenes de compra y registra entregas",
                Set.of(DASHBOARD_VER, PORTAL_PROVEEDOR)));
        r.put(INVENTARIO, new DefinicionRol(INVENTARIO, "Inventario",
                "Responsable de bodega: existencias, movimientos, mermas, ajustes y abastecimiento",
                Set.of(DASHBOARD_VER, INVENTARIO_VER, INVENTARIO_GESTIONAR, INVENTARIO_MOVIMIENTOS, INVENTARIO_AJUSTAR,
                        PROVEEDORES_VER, COMPRAS_GESTIONAR, MENU_VER)));
        r.put(ADMIN_PLATAFORMA, new DefinicionRol(ADMIN_PLATAFORMA, "Administrador de plataforma",
                "Gestiona usuarios, roles, parámetros generales y auditoría. No opera el restaurante",
                Set.of(DASHBOARD_VER, USUARIOS_GESTIONAR, ROLES_GESTIONAR, AUDITORIA_VER, PARAMETROS_GESTIONAR)));
        SISTEMA = Map.copyOf(r);
    }

    private Roles() {
    }

    public static boolean esRolSistema(String codigo) {
        return SISTEMA.containsKey(codigo);
    }
}
