package com.wokcontrol.usuarios.config;

import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.security.Roles;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.repository.UsuarioRepository;
import com.wokcontrol.usuarios.service.PoliticaPassword;
import com.wokcontrol.usuarios.service.RolService;

/**
 * Al iniciar: sincroniza los roles del sistema y, solo si no existe ningún usuario, crea el primer
 * administrador de plataforma con las credenciales de WOK_BOOTSTRAP_ADMIN_USERNAME y
 * WOK_BOOTSTRAP_ADMIN_PASSWORD. No hay usuarios ni contraseñas predeterminadas en el código.
 */
@Component
public class InicializacionDatos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializacionDatos.class);

    private final RolService roles;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public InicializacionDatos(RolService roles, UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${wok.bootstrap.admin-username:}") String adminUsername,
            @Value("${wok.bootstrap.admin-password:}") String adminPassword) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        roles.sincronizarRolesSistema();
        if (usuarios.count() > 0) {
            return;
        }
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            log.warn("No existe ningún usuario. Defina WOK_BOOTSTRAP_ADMIN_USERNAME y WOK_BOOTSTRAP_ADMIN_PASSWORD "
                    + "en el archivo .env y reinicie usuarios-service para crear el primer administrador de plataforma.");
            return;
        }
        PoliticaPassword.validar(adminPassword);
        Usuario admin = new Usuario();
        admin.setUsername(adminUsername.trim().toLowerCase(Locale.ROOT));
        admin.setNombre("Administrador de plataforma");
        admin.setPasswordHash(encoder.encode(adminPassword));
        admin.setRoles(Set.of(Roles.ADMIN_PLATAFORMA));
        admin.setActivo(true);
        admin.setDebeCambiarPassword(true);
        admin.setCreadoPor("bootstrap");
        usuarios.save(admin);
        log.warn("Se creó el administrador de plataforma '{}'. Deberá cambiar la contraseña en el primer ingreso; "
                + "elimine WOK_BOOTSTRAP_ADMIN_PASSWORD del archivo .env.", admin.getUsername());
    }
}
