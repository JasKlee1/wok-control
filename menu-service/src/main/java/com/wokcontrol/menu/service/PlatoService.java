package com.wokcontrol.menu.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.menu.client.InventarioClient;
import com.wokcontrol.menu.client.InventarioClient.InsumoResumen;
import com.wokcontrol.menu.dto.MenuDtos;
import com.wokcontrol.menu.dto.MenuDtos.IngredienteRequest;
import com.wokcontrol.menu.dto.MenuDtos.PlatoRequest;
import com.wokcontrol.menu.entity.Categoria;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.repository.PlatoRepository;

@Service
public class PlatoService {

    private final PlatoRepository repo;
    private final CategoriaService categorias;
    private final InventarioClient inventario;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;

    public PlatoService(PlatoRepository repo, CategoriaService categorias, InventarioClient inventario,
            MongoTemplate mongo, AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.categorias = categorias;
        this.inventario = inventario;
        this.mongo = mongo;
        this.auditoria = auditoria;
    }

    public List<Plato> buscar(String texto, String categoriaId, Boolean disponible, boolean incluirInactivos) {
        Query q = new Query();
        if (!incluirInactivos) {
            q.addCriteria(Criteria.where("activo").is(true));
        }
        if (texto != null && !texto.isBlank()) {
            q.addCriteria(Criteria.where("nombre").regex(".*" + Pattern.quote(texto.trim()) + ".*", "i"));
        }
        if (categoriaId != null && !categoriaId.isBlank()) {
            q.addCriteria(Criteria.where("categoriaId").is(categoriaId));
        }
        if (disponible != null) {
            q.addCriteria(Criteria.where("disponible").is(disponible));
        }
        q.with(Sort.by("nombre"));
        return mongo.find(q, Plato.class);
    }

    public Plato obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Plato", id));
    }

    /** Foto de cada categoría (las que tienen): se muestra en los platos que aún no tienen foto propia. */
    public Map<String, String> fotosCategorias() {
        return categorias.listar().stream().filter(c -> c.getImagen() != null)
                .collect(Collectors.toMap(Categoria::getId, c -> c.getImagen().id()));
    }

    public Map<String, String> nombresCategorias() {
        return categorias.listar().stream().collect(Collectors.toMap(Categoria::getId, Categoria::getNombre));
    }

    public Plato crear(PlatoRequest req) {
        if (repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe un plato llamado " + req.nombre());
        }
        Plato p = new Plato();
        p.setPrecio(req.precio());
        aplicar(p, req);
        p.getHistorialPrecios().add(new Plato.CambioPrecio(Instant.now(), null, req.precio(), UsuarioActual.responsableActual()));
        Plato g = repo.save(p);
        auditoria.registrar("CREAR", "Plato", g.getId(), g.getNombre() + " $" + g.getPrecio());
        return g;
    }

    public Plato actualizar(String id, PlatoRequest req) {
        Plato p = obtener(id);
        if (!p.getNombre().equalsIgnoreCase(req.nombre().trim()) && repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe un plato llamado " + req.nombre());
        }
        registrarCambioPrecio(p, req.precio());
        aplicar(p, req);
        Plato g = repo.save(p);
        auditoria.registrar("ACTUALIZAR", "Plato", id, g.getNombre());
        return g;
    }

    public Plato cambiarPrecio(String id, BigDecimal precio) {
        Plato p = obtener(id);
        registrarCambioPrecio(p, precio);
        return repo.save(p);
    }

    public Plato cambiarDisponibilidad(String id, boolean disponible) {
        Plato p = obtener(id);
        if (!p.isActivo() && disponible) {
            throw new ReglaNegocioException("El plato está retirado de la carta");
        }
        p.setDisponible(disponible);
        Plato g = repo.save(p);
        auditoria.registrar(disponible ? "HABILITAR" : "AGOTAR", "Plato", id, g.getNombre());
        return g;
    }

    /** Retiro lógico: el plato deja de ofrecerse, pero se conserva para el historial de pedidos. */
    public void retirar(String id) {
        Plato p = obtener(id);
        p.setActivo(false);
        p.setDisponible(false);
        repo.save(p);
        auditoria.registrar("RETIRAR", "Plato", id, p.getNombre());
    }

    private void registrarCambioPrecio(Plato p, BigDecimal nuevo) {
        if (p.getPrecio() == null || p.getPrecio().compareTo(nuevo) != 0) {
            p.getHistorialPrecios().add(new Plato.CambioPrecio(Instant.now(), p.getPrecio(), nuevo,
                    UsuarioActual.responsableActual()));
            auditoria.registrar("CAMBIAR_PRECIO", "Plato", p.getId(), p.getPrecio() + " -> " + nuevo);
            p.setPrecio(nuevo);
        }
    }

    private void aplicar(Plato p, PlatoRequest req) {
        Categoria c = categorias.obtener(req.categoriaId());
        if (!c.isActiva()) {
            throw new ReglaNegocioException("La categoría " + c.getNombre() + " está inactiva");
        }
        Set<String> alergenos = new LinkedHashSet<>();
        if (req.alergenos() != null) {
            for (String a : req.alergenos()) {
                if (!MenuDtos.ALERGENOS.contains(a)) {
                    throw new ReglaNegocioException("Alérgeno desconocido: " + a);
                }
                alergenos.add(a);
            }
        }
        p.setNombre(req.nombre().trim());
        p.setDescripcion(req.descripcion());
        p.setCategoriaId(c.getId());
        p.setDisponible(req.disponible());
        p.setActivo(true);
        p.setPicanteBase(req.picanteBase());
        p.setAlergenos(alergenos);
        p.setTiempoPreparacionMin(req.tiempoPreparacionMin());
        p.setReceta(construirReceta(req.receta()));
    }

    /**
     * Valida cada ingrediente contra inventario-service y guarda copia del nombre y la unidad.
     * Si la receta está vacía no se consulta inventario.
     */
    List<Plato.IngredienteReceta> construirReceta(List<IngredienteRequest> receta) {
        List<Plato.IngredienteReceta> resultado = new ArrayList<>();
        if (receta == null || receta.isEmpty()) {
            return resultado;
        }
        Map<String, InsumoResumen> insumos = inventario.insumosActivos().stream()
                .collect(Collectors.toMap(InsumoResumen::id, Function.identity()));
        Set<String> vistos = new HashSet<>();
        for (IngredienteRequest ing : receta) {
            InsumoResumen ins = insumos.get(ing.insumoId());
            if (ins == null) {
                throw new ReglaNegocioException("El insumo " + ing.insumoId() + " no existe o está inactivo en inventario");
            }
            if (!vistos.add(ing.insumoId())) {
                throw new ReglaNegocioException("El insumo " + ins.nombre() + " está repetido en la receta");
            }
            resultado.add(new Plato.IngredienteReceta(ins.id(), ins.nombre(), ins.unidad(), ing.cantidad()));
        }
        return resultado;
    }
}
