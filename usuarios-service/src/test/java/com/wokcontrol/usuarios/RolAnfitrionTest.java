package com.wokcontrol.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.web.Navegacion;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.usuarios.entity.Rol;
import com.wokcontrol.usuarios.repository.RolRepository;
import com.wokcontrol.usuarios.service.RolService;

/**
 * Épica 8 · T001 (HU-043): el administrador de plataforma tiene el rol ANFITRION listo para asignar.
 * Ve el salón y la lista de espera y cambia la ocupación, sin crear ni editar mesas, pedir ni cobrar.
 */
class RolAnfitrionTest {

    @Test
    void elRolAnfitrionTieneElPermisoMinimoDelSalon() {
        assertThat(Roles.SISTEMA).containsKey(Roles.ANFITRION);
        assertThat(Roles.SISTEMA.get(Roles.ANFITRION).permisos())
                .containsExactlyInAnyOrder(Permisos.DASHBOARD_VER, Permisos.SALON_OPERAR);
        assertThat(Permisos.existe(Permisos.SALON_OPERAR)).isTrue();
    }

    @Test
    void elAnfitrionSeAsignaAUsuariosDelPersonalYSeSincronizaAlArrancar() {
        RolRepository repo = mock(RolRepository.class);
        List<Rol> guardados = new ArrayList<>();
        when(repo.findById(any())).thenAnswer(i -> guardados.stream().filter(r -> r.getCodigo().equals(i.getArgument(0))).findFirst());
        when(repo.save(any(Rol.class))).thenAnswer(i -> {
            guardados.add(i.getArgument(0));
            return i.getArgument(0);
        });
        RolService roles = new RolService(repo, mock(AuditoriaPublisher.class));

        roles.sincronizarRolesSistema();
        assertThatCode(() -> roles.validarAsignables(Set.of(Roles.ANFITRION))).doesNotThrowAnyException();

        assertThat(guardados).filteredOn(r -> r.getCodigo().equals(Roles.ANFITRION)).singleElement()
                .satisfies(r -> assertThat(r.getPermisos()).containsExactlyInAnyOrder(Permisos.DASHBOARD_VER, Permisos.SALON_OPERAR));
    }

    @Test
    void elMenuDelAnfitrionMuestraElSalonYNoPedidosNiCaja() {
        UsuarioActual lina = new UsuarioActual("lina", "Lina", List.of(Roles.ANFITRION),
                Roles.SISTEMA.get(Roles.ANFITRION).permisos(), null, false, null, null);
        List<String> rutas = Navegacion.para(lina).stream().flatMap(s -> s.items().stream()).map(Navegacion.Item::href).toList();

        assertThat(rutas).contains("/salon", "/dashboard")
                .doesNotContain("/pedidos", "/mesas", "/caja/cuentas", "/cocina", "/menu/platos");
    }
}
