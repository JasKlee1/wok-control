package com.wokcontrol.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.JwtProperties;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.repository.UsuarioRepository;
import com.wokcontrol.usuarios.service.AuthService;
import com.wokcontrol.usuarios.service.CredencialesInvalidasException;
import com.wokcontrol.usuarios.service.RolService;

class AuthServiceTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UsuarioRepository repo;
    private RolService roles;
    private MongoTemplate mongo;
    private AuditoriaPublisher auditoria;
    private JwtDecoder decoder;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        roles = mock(RolService.class);
        mongo = mock(MongoTemplate.class);
        auditoria = mock(AuditoriaPublisher.class);
        JwtProperties props = new JwtProperties("clave-de-pruebas-unitarias-wok-control-1234", 60L, "wok-control",
                null, false, 5L);
        JwtConfig cfg = new JwtConfig();
        var key = cfg.wokJwtKey(props);
        decoder = cfg.jwtDecoder(key, props);
        TokenService tokens = new TokenService(cfg.jwtEncoder(key), props);
        auth = new AuthService(repo, roles, encoder, tokens, mongo, auditoria, 3, 15, "America/Bogota");
    }

    private Usuario usuario(String password, boolean activo) {
        Usuario u = new Usuario();
        u.setId("u1");
        u.setUsername("mesero1");
        u.setNombre("Ana Mesera");
        u.setPasswordHash(encoder.encode(password));
        u.setRoles(Set.of(Roles.MESERO));
        u.setActivo(activo);
        return u;
    }

    @Test
    void loginExitosoEmiteTokenConPermisosCalculadosEnServidor() {
        when(repo.findByUsername("mesero1")).thenReturn(Optional.of(usuario("Secreta123", true)));
        when(roles.permisosDe(Set.of(Roles.MESERO))).thenReturn(Set.of(Permisos.PEDIDOS_GESTIONAR, Permisos.MESAS_VER));

        AuthService.Sesion s = auth.autenticar("  MESERO1 ", "Secreta123");

        Jwt jwt = decoder.decode(s.token().token());
        assertThat(jwt.getSubject()).isEqualTo("mesero1");
        assertThat(jwt.getClaimAsStringList("permisos")).containsExactlyInAnyOrder("PEDIDOS_GESTIONAR", "MESAS_VER");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MESERO");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void usuarioInexistenteRecibeMensajeGenerico() {
        when(repo.findByUsername(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.autenticar("nadie", "x"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Usuario o contraseña incorrectos");
    }

    @Test
    void passwordIncorrectaIncrementaIntentosYBloqueaAlLlegarAlMaximo() {
        Usuario u = usuario("Secreta123", true);
        when(repo.findByUsername("mesero1")).thenReturn(Optional.of(u));
        Usuario conIntentos = usuario("Secreta123", true);
        conIntentos.setIntentosFallidos(3);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Usuario.class)))
                .thenReturn(conIntentos);

        assertThatThrownBy(() -> auth.autenticar("mesero1", "mala"))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(auditoria).registrar(eq("BLOQUEO_CUENTA"), eq("Usuario"), eq("mesero1"), anyString());
    }

    @Test
    void cuentaBloqueadaNoPermiteIngresarAunConPasswordCorrecta() {
        Usuario u = usuario("Secreta123", true);
        u.setBloqueadoHasta(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repo.findByUsername("mesero1")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> auth.autenticar("mesero1", "Secreta123"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("bloqueada");
        verify(roles, never()).permisosDe(any());
    }

    @Test
    void cuentaInactivaNoPermiteIngresar() {
        when(repo.findByUsername("mesero1")).thenReturn(Optional.of(usuario("Secreta123", false)));
        assertThatThrownBy(() -> auth.autenticar("mesero1", "Secreta123"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageContaining("desactivada");
    }
}
