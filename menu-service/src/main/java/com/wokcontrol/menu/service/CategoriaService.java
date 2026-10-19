package com.wokcontrol.menu.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.menu.dto.MenuDtos.CategoriaRequest;
import com.wokcontrol.menu.entity.Categoria;
import com.wokcontrol.menu.repository.CategoriaRepository;
import com.wokcontrol.menu.repository.PlatoRepository;

@Service
public class CategoriaService {

    private final CategoriaRepository repo;
    private final PlatoRepository platos;
    private final AuditoriaPublisher auditoria;

    public CategoriaService(CategoriaRepository repo, PlatoRepository platos, AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.platos = platos;
        this.auditoria = auditoria;
    }

    public List<Categoria> listar() {
        return repo.findAllByOrderByOrdenAscNombreAsc();
    }

    public Categoria obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Categoría", id));
    }

    public Categoria crear(CategoriaRequest req) {
        if (repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe la categoría " + req.nombre());
        }
        Categoria c = new Categoria();
        aplicar(c, req);
        Categoria g = repo.save(c);
        auditoria.registrar("CREAR", "Categoria", g.getId(), g.getNombre());
        return g;
    }

    public Categoria actualizar(String id, CategoriaRequest req) {
        Categoria c = obtener(id);
        if (!c.getNombre().equalsIgnoreCase(req.nombre().trim()) && repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe la categoría " + req.nombre());
        }
        if (c.isActiva() && !req.activa() && platos.countByCategoriaIdAndActivoTrue(id) > 0) {
            throw new ReglaNegocioException("No se puede desactivar una categoría con platos activos; muévalos o desactívelos primero");
        }
        aplicar(c, req);
        Categoria g = repo.save(c);
        auditoria.registrar("ACTUALIZAR", "Categoria", id, g.getNombre() + (g.isActiva() ? "" : " (inactiva)"));
        return g;
    }

    private static void aplicar(Categoria c, CategoriaRequest req) {
        c.setNombre(req.nombre().trim());
        c.setDescripcion(req.descripcion());
        c.setOrden(req.orden());
        c.setActiva(req.activa());
    }
}
