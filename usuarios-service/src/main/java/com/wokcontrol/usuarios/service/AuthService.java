package com.wokcontrol.usuarios.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.common.security.TokenService.TokenEmitido;
import com.wokcontrol.usuarios.dto.AuthDtos.PerfilResponse;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.repository.UsuarioRepository;

/**
 * Autenticación de usuarios: verifica la contraseña (BCrypt), bloquea temporalmente la cuenta tras
 * varios intentos fallidos y emite el JWT con los permisos calculados en el servidor.
 */
@Service
public class AuthService {

    static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarios;
    private final RolService roles;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;
    private final int maxIntentos;
    private final int minutosBloqueo;
    private final ZoneId zona;
    /** Hash ficticio para igualar el tiempo de respuesta cuando el usuario no existe. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, RolService roles, PasswordEncoder encoder, TokenService tokens,
            MongoTemplate mongo, AuditoriaPublisher auditoria,
            @Value("${wok.login.max-intentos:5}") int maxIntentos,
            @Value("${wok.login.bloqueo-minutos:15}") int minutosBloqueo,
            @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
        this.tokens = tokens;
        this.mongo = mongo;
        this.auditoria = auditoria;
        this.maxIntentos = maxIntentos;
        this.minutosBloqueo = minutosBloqueo;
        this.zona = ZoneId.of(zona);
        this.hashFicticio = encoder.encode("wok-control-hash-ficticio");
    }

    public record Sesion(TokenEmitido token, PerfilResponse perfil) {
    }

    public Sesion autenticar(String usernameIngresado, String password) {
        String username = usernameIngresado == null ? "" : usernameIngresado.trim().toLowerCase(Locale.ROOT);
        Usuario u = usuarios.findByUsername(username).orElse(null);
        if (u == null) {
            encoder.matches(password, hashFicticio);
            throw new CredencialesInvalidasException(MENSAJE_GENERICO);
        }
        Instant ahora = Instant.now();
        if (u.estaBloqueado(ahora)) {
            throw new CredencialesInvalidasException("La cuenta está bloqueada temporalmente por intentos fallidos. "
                    + "Intente de nuevo después de las " + DateTimeFormatter.ofPattern("HH:mm").format(u.getBloqueadoHasta().atZone(zona)));
        }
        if (!encoder.matches(password, u.getPasswordHash())) {
            registrarFallo(u, ahora);
            throw new CredencialesInvalidasException(MENSAJE_GENERICO);
        }
        if (!u.isActivo()) {
            throw new CredencialesInvalidasException("La cuenta está desactivada. Contacte al administrador de la plataforma");
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(u.getId())),
                new Update().set("intentosFallidos", 0).unset("bloqueadoHasta").set("ultimoAcceso", ahora), Usuario.class);

        Set<String> permisos = roles.permisosDe(u.getRoles());
        TokenEmitido token = tokens.emitirTokenUsuario(u.getUsername(), u.getNombre(), u.getRoles(), permisos,
                u.getProveedorId());
        PerfilResponse perfil = new PerfilResponse(u.getUsername(), u.getNombre(), u.getEmail(),
                new ArrayList<>(u.getRoles()), permisos, u.getProveedorId(), u.isDebeCambiarPassword());
        return new Sesion(token, perfil);
    }

    private void registrarFallo(Usuario u, Instant ahora) {
        Usuario actualizado = mongo.findAndModify(Query.query(Criteria.where("_id").is(u.getId())),
                new Update().inc("intentosFallidos", 1),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), Usuario.class);
        if (actualizado != null && actualizado.getIntentosFallidos() >= maxIntentos) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(u.getId())),
                    new Update().set("intentosFallidos", 0).set("bloqueadoHasta", ahora.plus(minutosBloqueo, ChronoUnit.MINUTES)),
                    Usuario.class);
            auditoria.registrar("BLOQUEO_CUENTA", "Usuario", u.getUsername(),
                    "Cuenta bloqueada " + minutosBloqueo + " minutos tras " + maxIntentos + " intentos fallidos");
        }
    }
}
