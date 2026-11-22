package com.wokcontrol.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.common.web.Navegacion;

/**
 * v2 · Sprint 3 · PBI-19 (CR-001): rol DOMICILIARIO con permisos mínimos (RN-15) y el cajero/administrador
 * con la gestión de domicilios.
 */
class RolDomiciliarioTest {

    private static List<String> rutas(String rol) {
        UsuarioActual u = new UsuarioActual("u", "U", List.of(rol), Roles.SISTEMA.get(rol).permisos(), null, false, null, null);
        return Navegacion.para(u).stream().flatMap(s -> s.items().stream()).map(Navegacion.Item::href).toList();
    }

    @Test
    void elDomiciliarioSoloReparte() {
        assertThat(Roles.SISTEMA.get(Roles.DOMICILIARIO).permisos())
                .containsExactlyInAnyOrder(Permisos.DASHBOARD_VER, Permisos.DOMICILIOS_REPARTIR);
        assertThat(Permisos.existe(Permisos.DOMICILIOS_GESTIONAR)).isTrue();
        assertThat(Permisos.existe(Permisos.DOMICILIOS_REPARTIR)).isTrue();
    }

    @Test
    void cajeroYAdministradorGestionanDomicilios() {
        assertThat(Roles.SISTEMA.get(Roles.CAJERO).permisos()).contains(Permisos.DOMICILIOS_GESTIONAR)
                .doesNotContain(Permisos.DOMICILIOS_REPARTIR);
        assertThat(Roles.SISTEMA.get(Roles.ADMINISTRADOR).permisos()).contains(Permisos.DOMICILIOS_GESTIONAR);
        assertThat(Roles.SISTEMA.get(Roles.MESERO).permisos()).doesNotContain(Permisos.DOMICILIOS_GESTIONAR,
                Permisos.DOMICILIOS_REPARTIR);
    }

    @Test
    void menuSegunElRol() {
        assertThat(rutas(Roles.DOMICILIARIO)).contains("/mis-entregas")
                .doesNotContain("/domicilios", "/caja/cuentas", "/pedidos", "/inventario/insumos");
        assertThat(rutas(Roles.CAJERO)).contains("/domicilios").doesNotContain("/mis-entregas");
    }
}
