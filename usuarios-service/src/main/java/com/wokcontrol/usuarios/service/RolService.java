package com.wokcontrol.usuarios.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.usuarios.dto.RolDtos.RolRequest;
import com.wokcontrol.usuarios.entity.Rol;
import com.wokcontrol.usuarios.repository.RolRepository;

@Service
public class RolService {

    private static final Logger log = LoggerFactory.getLogger(RolService.class);

    private final RolRepository repo;
    private final AuditoriaPublisher auditoria;

    public RolService(RolRepository repo, AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.auditoria = auditoria;
    }

    /** Crea o corrige los 7 roles del sistema según la matriz definida en {@link Roles}. */
    public void sincronizarRolesSistema() {
        for (Roles.DefinicionRol def : Roles.SISTEMA.values()) {
            Rol rol = repo.findById(def.codigo()).orElseGet(Rol::new);
            rol.setCodigo(def.codigo());
            rol.setNombre(def.nombre());
            rol.setDescripcion(def.descripcion());
            rol.setPermisos(new TreeSet<>(def.permisos()));
            rol.setSistema(true);
            rol.setActivo(true);
            repo.save(rol);
        }
        log.info("Roles del sistema sincronizados: {}", Roles.SISTEMA.keySet());
    }

    public List<Rol> listar() {
        return repo.findAllByOrderBySistemaDescNombreAsc();
    }

    public Rol obtener(String codigo) {
        return repo.findById(codigo).orElseThrow(() -> new RecursoNoEncontradoException("Rol", codigo));
    }

    public Rol crear(RolRequest req) {
        if (Roles.esRolSistema(req.codigo()) || repo.existsById(req.codigo())) {
            throw new ConflictoException("Ya existe un rol con el código " + req.codigo());
        }
        Rol rol = new Rol();
        rol.setCodigo(req.codigo());
        aplicar(rol, req);
        Rol guardado = repo.save(rol);
        auditoria.registrar("CREAR", "Rol", guardado.getCodigo(), "Permisos: " + guardado.getPermisos());
        return guardado;
    }

    public Rol actualizar(String codigo, RolRequest req) {
        Rol rol = obtener(codigo);
        if (rol.isSistema()) {
            throw new ReglaNegocioException("Los roles del sistema no se pueden modificar; cree un rol personalizado");
        }
        aplicar(rol, req);
        Rol guardado = repo.save(rol);
        auditoria.registrar("ACTUALIZAR", "Rol", codigo, "Permisos: " + guardado.getPermisos() + ", activo: " + guardado.isActivo());
        return guardado;
    }

    private void aplicar(Rol rol, RolRequest req) {
        Set<String> permisos = new TreeSet<>();
        for (String p : req.permisos()) {
            if (!Permisos.existe(p)) {
                throw new ReglaNegocioException("Permiso desconocido: " + p);
            }
            permisos.add(p);
        }
        rol.setNombre(req.nombre().trim());
        rol.setDescripcion(req.descripcion());
        rol.setPermisos(permisos);
        rol.setSistema(false);
        rol.setActivo(req.activo());
    }

    /** Valida que todos los códigos existan y estén activos. */
    public void validarAsignables(Set<String> codigos) {
        for (String c : codigos) {
            Rol r = repo.findById(c).orElseThrow(() -> new ReglaNegocioException("El rol " + c + " no existe"));
            if (!r.isActivo()) {
                throw new ReglaNegocioException("El rol " + c + " está inactivo");
            }
        }
    }

    /** Unión de permisos de los roles activos del usuario. */
    public Set<String> permisosDe(Set<String> codigos) {
        Set<String> permisos = new LinkedHashSet<>();
        repo.findAllById(codigos).forEach(r -> {
            if (r.isActivo()) {
                permisos.addAll(r.getPermisos());
            }
        });
        permisos.remove(Permisos.SISTEMA);
        return permisos;
    }
}
