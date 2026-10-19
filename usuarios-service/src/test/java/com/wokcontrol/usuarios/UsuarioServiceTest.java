package com.wokcontrol.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.usuarios.dto.UsuarioDtos.ActualizarUsuarioRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.CrearUsuarioRequest;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.repository.UsuarioRepository;
import com.wokcontrol.usuarios.service.RolService;
import com.wokcontrol.usuarios.service.UsuarioService;

class UsuarioServiceTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UsuarioRepository repo;
    private UsuarioService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new UsuarioService(repo, mock(RolService.class), encoder, mock(MongoTemplate.class),
                mock(AuditoriaPublisher.class));
    }

    @Test
    void creaUsuarioConHashYObligaACambiarPassword() {
        Usuario u = servicio.crear(new CrearUsuarioRequest("Cajero1", "Luis", "LUIS@wok.co", "Inicial123",
                Set.of(Roles.CAJERO), null), "plataforma");

        assertThat(u.getUsername()).isEqualTo("cajero1");
        assertThat(u.getEmail()).isEqualTo("luis@wok.co");
        assertThat(u.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("Inicial123");
        assertThat(encoder.matches("Inicial123", u.getPasswordHash())).isTrue();
        assertThat(u.isDebeCambiarPassword()).isTrue();
    }

    @Test
    void rechazaUsuarioDuplicado() {
        when(repo.existsByUsername("cajero1")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("cajero1", "Luis", null, "Inicial123",
                Set.of(Roles.CAJERO), null), "plataforma")).isInstanceOf(ConflictoException.class);
    }

    @Test
    void exigeContrasenaConLetrasYNumeros() {
        assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("cajero2", "Luis", null, "soloLetras",
                Set.of(Roles.CAJERO), null), "plataforma"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("número");
    }

    @Test
    void rolProveedorRequiereVinculoConProveedor() {
        assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("prov1", "Distribuidora", null, "Inicial123",
                Set.of(Roles.PROVEEDOR), " "), "plataforma"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("proveedor");
    }

    @Test
    void noPermiteDesactivarAlUltimoAdministradorDePlataforma() {
        Usuario admin = new Usuario();
        admin.setId("a1");
        admin.setUsername("plataforma");
        admin.setRoles(Set.of(Roles.ADMIN_PLATAFORMA));
        admin.setActivo(true);
        when(repo.findById("a1")).thenReturn(Optional.of(admin));
        when(repo.countByRolesContainingAndActivoTrue(Roles.ADMIN_PLATAFORMA)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.cambiarEstado("a1", false, "otro"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos un administrador");
        assertThatThrownBy(() -> servicio.actualizar("a1",
                new ActualizarUsuarioRequest("Admin", null, Set.of(Roles.CAJERO), null), "otro"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void noPermiteDesactivarLaPropiaCuenta() {
        Usuario u = new Usuario();
        u.setId("u1");
        u.setUsername("yo");
        u.setRoles(Set.of(Roles.CAJERO));
        when(repo.findById("u1")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> servicio.cambiarEstado("u1", false, "yo")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void cambioDePasswordPropiaVerificaLaActual() {
        Usuario u = new Usuario();
        u.setUsername("yo");
        u.setPasswordHash(encoder.encode("Actual123"));
        u.setDebeCambiarPassword(true);
        when(repo.findByUsername("yo")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> servicio.cambiarPasswordPropia("yo", "otra", "Nueva1234", "Nueva1234"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.cambiarPasswordPropia("yo", "Actual123", "Nueva1234", "Distinta1"))
                .isInstanceOf(ReglaNegocioException.class);

        servicio.cambiarPasswordPropia("yo", "Actual123", "Nueva1234", "Nueva1234");
        assertThat(encoder.matches("Nueva1234", u.getPasswordHash())).isTrue();
        assertThat(u.isDebeCambiarPassword()).isFalse();
    }
}
