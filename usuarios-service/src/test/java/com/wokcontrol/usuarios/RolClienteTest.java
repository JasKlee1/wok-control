package com.wokcontrol.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.usuarios.repository.RolRepository;
import com.wokcontrol.usuarios.service.RolService;

/**
 * Épica 7 · T001 (HU-036): el rol CLIENTE existe en el sistema, solo ve la carta de su mesa y no
 * se asigna a usuarios del personal (es una sesión de mesa sin usuario ni contraseña).
 */
class RolClienteTest {

    @Test
    void elRolClienteExisteYSoloTieneElPermisoDeCartaDeMesa() {
        assertThat(Roles.SISTEMA).containsKey(Roles.CLIENTE);
        assertThat(Roles.SISTEMA.get(Roles.CLIENTE).permisos()).containsExactly(Permisos.CARTA_CLIENTE);
    }

    @Test
    void elRolClienteNoSeAsignaAUsuariosDelPersonal() {
        RolService roles = new RolService(mock(RolRepository.class), mock(AuditoriaPublisher.class));

        assertThatThrownBy(() -> roles.validarAsignables(Set.of(Roles.CLIENTE)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("sesión de mesa");
    }
}
