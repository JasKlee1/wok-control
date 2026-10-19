package com.wokcontrol.usuarios.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.usuarios.dto.UsuarioDtos.ActualizarUsuarioRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.CrearUsuarioRequest;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository repo;
    private final RolService roles;
    private final PasswordEncoder encoder;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;

    public UsuarioService(UsuarioRepository repo, RolService roles, PasswordEncoder encoder, MongoTemplate mongo,
            AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.roles = roles;
        this.encoder = encoder;
        this.mongo = mongo;
        this.auditoria = auditoria;
    }

    public Page<Usuario> buscar(String texto, String rol, Boolean activo, Pageable pageable) {
        Query q = new Query();
        if (texto != null && !texto.isBlank()) {
            String rx = ".*" + Pattern.quote(texto.trim()) + ".*";
            q.addCriteria(new Criteria().orOperator(Criteria.where("username").regex(rx, "i"),
                    Criteria.where("nombre").regex(rx, "i"), Criteria.where("email").regex(rx, "i")));
        }
        if (rol != null && !rol.isBlank()) {
            q.addCriteria(Criteria.where("roles").is(rol));
        }
        if (activo != null) {
            q.addCriteria(Criteria.where("activo").is(activo));
        }
        long total = mongo.count(q, Usuario.class);
        q.with(pageable).with(Sort.by("nombre"));
        List<Usuario> lista = mongo.find(q, Usuario.class);
        return new PageImpl<>(lista, pageable, total);
    }

    public Usuario obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    public Usuario obtenerPorUsername(String username) {
        return repo.findByUsername(username).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", username));
    }

    public Usuario crear(CrearUsuarioRequest req, String creadoPor) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        if (repo.existsByUsername(username)) {
            throw new ConflictoException("El usuario " + username + " ya existe");
        }
        String email = normalizarEmail(req.email());
        if (email != null && repo.existsByEmail(email)) {
            throw new ConflictoException("El correo " + email + " ya está registrado");
        }
        PoliticaPassword.validar(req.password());
        Set<String> rolesAsignados = new LinkedHashSet<>(req.roles());
        roles.validarAsignables(rolesAsignados);

        Usuario u = new Usuario();
        u.setUsername(username);
        u.setNombre(req.nombre().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRoles(rolesAsignados);
        u.setProveedorId(validarProveedor(rolesAsignados, req.proveedorId()));
        u.setActivo(true);
        u.setDebeCambiarPassword(true);
        u.setCreadoPor(creadoPor);
        Usuario guardado = repo.save(u);
        auditoria.registrar("CREAR", "Usuario", username, "Roles: " + rolesAsignados);
        return guardado;
    }

    public Usuario actualizar(String id, ActualizarUsuarioRequest req, String actor) {
        Usuario u = obtener(id);
        String email = normalizarEmail(req.email());
        if (email != null && !email.equals(u.getEmail()) && repo.existsByEmail(email)) {
            throw new ConflictoException("El correo " + email + " ya está registrado");
        }
        Set<String> nuevosRoles = new LinkedHashSet<>(req.roles());
        roles.validarAsignables(nuevosRoles);
        if (u.getRoles().contains(Roles.ADMIN_PLATAFORMA) && !nuevosRoles.contains(Roles.ADMIN_PLATAFORMA)) {
            protegerUltimoAdminPlataforma(u, actor);
        }
        u.setNombre(req.nombre().trim());
        u.setEmail(email);
        u.setRoles(nuevosRoles);
        u.setProveedorId(validarProveedor(nuevosRoles, req.proveedorId()));
        Usuario guardado = repo.save(u);
        auditoria.registrar("ACTUALIZAR", "Usuario", u.getUsername(), "Roles: " + nuevosRoles);
        return guardado;
    }

    public Usuario cambiarEstado(String id, boolean activo, String actor) {
        Usuario u = obtener(id);
        if (!activo) {
            if (u.getUsername().equals(actor)) {
                throw new ReglaNegocioException("No puede desactivar su propia cuenta");
            }
            if (u.getRoles().contains(Roles.ADMIN_PLATAFORMA)) {
                protegerUltimoAdminPlataforma(u, actor);
            }
        }
        u.setActivo(activo);
        if (activo) {
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(null);
        }
        Usuario guardado = repo.save(u);
        auditoria.registrar(activo ? "ACTIVAR" : "DESACTIVAR", "Usuario", u.getUsername(), null);
        return guardado;
    }

    public void restablecerPassword(String id, String nueva) {
        PoliticaPassword.validar(nueva);
        Usuario u = obtener(id);
        u.setPasswordHash(encoder.encode(nueva));
        u.setDebeCambiarPassword(true);
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        repo.save(u);
        auditoria.registrar("RESTABLECER_PASSWORD", "Usuario", u.getUsername(), "Debe cambiarla en el próximo ingreso");
    }

    public void cambiarPasswordPropia(String username, String actual, String nueva, String confirmacion) {
        Usuario u = obtenerPorUsername(username);
        if (!encoder.matches(actual, u.getPasswordHash())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta");
        }
        if (!nueva.equals(confirmacion)) {
            throw new ReglaNegocioException("La confirmación no coincide con la nueva contraseña");
        }
        if (encoder.matches(nueva, u.getPasswordHash())) {
            throw new ReglaNegocioException("La nueva contraseña debe ser diferente a la actual");
        }
        PoliticaPassword.validar(nueva);
        u.setPasswordHash(encoder.encode(nueva));
        u.setDebeCambiarPassword(false);
        repo.save(u);
        auditoria.registrar("CAMBIAR_PASSWORD", "Usuario", username, null);
    }

    public Usuario actualizarDatosPropios(String username, String nombre, String emailIngresado) {
        Usuario u = obtenerPorUsername(username);
        String email = normalizarEmail(emailIngresado);
        if (email != null && !email.equals(u.getEmail()) && repo.existsByEmail(email)) {
            throw new ConflictoException("El correo " + email + " ya está registrado");
        }
        if (nombre == null || nombre.isBlank() || nombre.length() > 100) {
            throw new ReglaNegocioException("El nombre es obligatorio (máximo 100 caracteres)");
        }
        u.setNombre(nombre.trim());
        u.setEmail(email);
        return repo.save(u);
    }

    private void protegerUltimoAdminPlataforma(Usuario u, String actor) {
        if (u.isActivo() && repo.countByRolesContainingAndActivoTrue(Roles.ADMIN_PLATAFORMA) <= 1) {
            throw new ReglaNegocioException("Debe existir al menos un administrador de plataforma activo");
        }
    }

    private static String validarProveedor(Set<String> roles, String proveedorId) {
        if (roles.contains(Roles.PROVEEDOR)) {
            if (proveedorId == null || proveedorId.isBlank()) {
                throw new ReglaNegocioException("Un usuario con rol Proveedor debe estar vinculado a un proveedor registrado");
            }
            return proveedorId.trim();
        }
        return null;
    }

    private static String normalizarEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
