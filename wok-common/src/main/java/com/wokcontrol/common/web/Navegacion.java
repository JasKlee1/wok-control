package com.wokcontrol.common.web;

import static com.wokcontrol.common.security.Permisos.*;

import java.util.ArrayList;
import java.util.List;

import com.wokcontrol.common.security.UsuarioActual;

/**
 * Menú lateral único para toda la plataforma. Cada opción declara los permisos que la habilitan;
 * se oculta si el usuario no tiene ninguno (la autorización real se valida en el backend).
 */
public final class Navegacion {

    public record Item(String etiqueta, String icono, String href, String[] permisos) {
        boolean visiblePara(UsuarioActual u) {
            return permisos.length == 0 || u.tieneAlguno(permisos);
        }
    }

    public record Seccion(String titulo, List<Item> items) {
    }

    private static final List<Seccion> SECCIONES = List.of(
            new Seccion("General", List.of(
                    new Item("Panel de control", "bi-speedometer2", "/dashboard", new String[] { DASHBOARD_VER }),
                    new Item("Reportes", "bi-bar-chart-line", "/reportes", new String[] { REPORTES_VER }))),
            new Seccion("Servicio", List.of(
                    new Item("Mesas", "bi-grid-3x3-gap", "/mesas", new String[] { MESAS_VER }),
                    new Item("Pedidos", "bi-receipt", "/pedidos", new String[] { PEDIDOS_VER }),
                    new Item("Cocina", "bi-fire", "/cocina", new String[] { COCINA_OPERAR }))),
            new Seccion("Carta", List.of(
                    new Item("Platos", "bi-egg-fried", "/menu/platos", new String[] { MENU_VER }),
                    new Item("Categorías", "bi-tags", "/menu/categorias", new String[] { MENU_GESTIONAR }),
                    new Item("Combos", "bi-collection", "/menu/combos", new String[] { MENU_VER }))),
            new Seccion("Inventario", List.of(
                    new Item("Insumos", "bi-box-seam", "/inventario/insumos", new String[] { INVENTARIO_VER }),
                    new Item("Movimientos", "bi-arrow-left-right", "/inventario/movimientos", new String[] { INVENTARIO_VER }),
                    new Item("Alertas de stock", "bi-exclamation-triangle", "/inventario/alertas", new String[] { INVENTARIO_VER }))),
            new Seccion("Abastecimiento", List.of(
                    new Item("Proveedores", "bi-truck", "/proveedores", new String[] { PROVEEDORES_VER }),
                    new Item("Órdenes de compra", "bi-clipboard-check", "/compras/ordenes", new String[] { COMPRAS_GESTIONAR, PROVEEDORES_VER }),
                    new Item("Mi portal", "bi-shop", "/portal-proveedor", new String[] { PORTAL_PROVEEDOR }))),
            new Seccion("Caja", List.of(
                    new Item("Cuentas y cobro", "bi-cash-coin", "/caja/cuentas", new String[] { FACTURACION_COBRAR }),
                    new Item("Comprobantes", "bi-file-earmark-text", "/caja/comprobantes", new String[] { FACTURACION_VER }),
                    new Item("Cierres de caja", "bi-safe", "/caja/cierres", new String[] { CAJA_CERRAR, FACTURACION_VER }),
                    new Item("Descuentos", "bi-percent", "/caja/descuentos", new String[] { DESCUENTOS_AUTORIZAR }))),
            new Seccion("Plataforma", List.of(
                    new Item("Usuarios", "bi-people", "/plataforma/usuarios", new String[] { USUARIOS_GESTIONAR }),
                    new Item("Roles y permisos", "bi-shield-lock", "/plataforma/roles", new String[] { ROLES_GESTIONAR }),
                    new Item("Parámetros", "bi-sliders", "/plataforma/parametros", new String[] { PARAMETROS_GESTIONAR }),
                    new Item("Auditoría", "bi-journal-text", "/plataforma/auditoria", new String[] { AUDITORIA_VER }))),
            new Seccion("Ayuda", List.of(
                    new Item("Documentación API", "bi-code-square", "/docs-api", new String[] {}))));

    private Navegacion() {
    }

    public static List<Seccion> para(UsuarioActual u) {
        List<Seccion> resultado = new ArrayList<>();
        for (Seccion s : SECCIONES) {
            List<Item> visibles = s.items().stream().filter(i -> i.visiblePara(u)).toList();
            if (!visibles.isEmpty()) {
                resultado.add(new Seccion(s.titulo(), visibles));
            }
        }
        return resultado;
    }
}
