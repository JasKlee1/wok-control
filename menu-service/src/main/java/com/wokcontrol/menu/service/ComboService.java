package com.wokcontrol.menu.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.menu.dto.MenuDtos.ComboRequest;
import com.wokcontrol.menu.dto.MenuDtos.ComboResponse;
import com.wokcontrol.menu.dto.MenuDtos.ItemComboRequest;
import com.wokcontrol.menu.entity.Combo;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.repository.ComboRepository;
import com.wokcontrol.menu.repository.PlatoRepository;

@Service
public class ComboService {

    private final ComboRepository repo;
    private final PlatoRepository platos;
    private final AuditoriaPublisher auditoria;

    public ComboService(ComboRepository repo, PlatoRepository platos, AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.platos = platos;
        this.auditoria = auditoria;
    }

    public List<Combo> listar(boolean incluirInactivos) {
        return repo.findAllByOrderByNombreAsc().stream().filter(c -> incluirInactivos || c.isActivo()).toList();
    }

    public Combo obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Combo", id));
    }

    public Combo crear(ComboRequest req) {
        if (repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe un combo llamado " + req.nombre());
        }
        Combo c = new Combo();
        aplicar(c, req);
        c.getHistorialPrecios().add(new Plato.CambioPrecio(Instant.now(), null, req.precio(), UsuarioActual.responsableActual()));
        Combo g = repo.save(c);
        auditoria.registrar("CREAR", "Combo", g.getId(), g.getNombre());
        return g;
    }

    public Combo actualizar(String id, ComboRequest req) {
        Combo c = obtener(id);
        if (!c.getNombre().equalsIgnoreCase(req.nombre().trim()) && repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe un combo llamado " + req.nombre());
        }
        if (c.getPrecio() != null && c.getPrecio().compareTo(req.precio()) != 0) {
            c.getHistorialPrecios().add(new Plato.CambioPrecio(Instant.now(), c.getPrecio(), req.precio(),
                    UsuarioActual.responsableActual()));
        }
        aplicar(c, req);
        Combo g = repo.save(c);
        auditoria.registrar("ACTUALIZAR", "Combo", id, g.getNombre());
        return g;
    }

    public Combo cambiarDisponibilidad(String id, boolean disponible) {
        Combo c = obtener(id);
        c.setDisponible(disponible);
        return repo.save(c);
    }

    public void retirar(String id) {
        Combo c = obtener(id);
        c.setActivo(false);
        c.setDisponible(false);
        repo.save(c);
        auditoria.registrar("RETIRAR", "Combo", id, c.getNombre());
    }

    private void aplicar(Combo c, ComboRequest req) {
        Map<String, Integer> cantidades = new LinkedHashMap<>();
        for (ItemComboRequest i : req.items()) {
            cantidades.merge(i.platoId(), i.cantidad(), Integer::sum);
        }
        List<Combo.ItemCombo> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cantidades.entrySet()) {
            Plato p = platos.findById(e.getKey()).orElseThrow(() -> new ReglaNegocioException("El plato " + e.getKey() + " no existe"));
            if (!p.isActivo()) {
                throw new ReglaNegocioException("El plato " + p.getNombre() + " está retirado de la carta");
            }
            items.add(new Combo.ItemCombo(p.getId(), p.getNombre(), e.getValue()));
        }
        c.setNombre(req.nombre().trim());
        c.setDescripcion(req.descripcion());
        c.setItems(items);
        c.setPrecio(req.precio());
        c.setDisponible(req.disponible());
        c.setActivo(true);
    }

    /** Un combo solo puede venderse si está marcado disponible y todos sus platos están disponibles. */
    public String motivoNoDisponible(Combo c, Map<String, Plato> platosPorId) {
        if (!c.isActivo()) {
            return "Combo retirado de la carta";
        }
        if (!c.isDisponible()) {
            return "Combo no disponible";
        }
        for (Combo.ItemCombo i : c.getItems()) {
            Plato p = platosPorId.get(i.platoId());
            if (p == null || !p.isActivo() || !p.isDisponible()) {
                return "No hay disponibilidad de " + i.nombrePlato();
            }
        }
        return null;
    }

    public ComboResponse aRespuesta(Combo c) {
        Map<String, Plato> porId = new LinkedHashMap<>();
        platos.findAllById(c.getItems().stream().map(Combo.ItemCombo::platoId).toList()).forEach(p -> porId.put(p.getId(), p));
        BigDecimal sueltos = BigDecimal.ZERO;
        for (Combo.ItemCombo i : c.getItems()) {
            Plato p = porId.get(i.platoId());
            if (p != null && p.getPrecio() != null) {
                sueltos = sueltos.add(p.getPrecio().multiply(BigDecimal.valueOf(i.cantidad())));
            }
        }
        return new ComboResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.getItems(), c.getPrecio(), sueltos,
                c.isDisponible(), motivoNoDisponible(c, porId) == null, c.isActivo(), c.getImagen());
    }
}
