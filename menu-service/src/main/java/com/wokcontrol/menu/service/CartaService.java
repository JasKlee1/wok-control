package com.wokcontrol.menu.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.menu.dto.MenuDtos.ConsumoInsumo;
import com.wokcontrol.menu.dto.MenuDtos.CotizacionRequest;
import com.wokcontrol.menu.dto.MenuDtos.CotizacionResponse;
import com.wokcontrol.menu.dto.MenuDtos.ItemCotizado;
import com.wokcontrol.menu.dto.MenuDtos.ItemCotizar;
import com.wokcontrol.menu.dto.MenuDtos.ProductoCarta;
import com.wokcontrol.menu.dto.MenuDtos.SeccionCarta;
import com.wokcontrol.menu.dto.MenuDtos.TipoProducto;
import com.wokcontrol.menu.entity.Categoria;
import com.wokcontrol.menu.entity.Combo;
import com.wokcontrol.menu.entity.Imagen;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.repository.ComboRepository;
import com.wokcontrol.menu.repository.PlatoRepository;

/**
 * Carta vigente para la toma de pedidos y cotización de productos: precio actual (que pedidos-service
 * copia en el pedido), disponibilidad y receta expandida en insumos por unidad vendida.
 */
@Service
public class CartaService {

    private final PlatoRepository platos;
    private final ComboRepository combos;
    private final CategoriaService categorias;
    private final ComboService comboService;

    public CartaService(PlatoRepository platos, ComboRepository combos, CategoriaService categorias,
            ComboService comboService) {
        this.platos = platos;
        this.combos = combos;
        this.categorias = categorias;
        this.comboService = comboService;
    }

    public List<SeccionCarta> cartaDisponible() {
        List<Plato> activos = platos.findByActivoTrueOrderByNombreAsc();
        Map<String, Plato> porId = new LinkedHashMap<>();
        activos.forEach(p -> porId.put(p.getId(), p));
        List<SeccionCarta> secciones = new ArrayList<>();
        for (Categoria c : categorias.listar()) {
            if (!c.isActiva()) {
                continue;
            }
            String fotoCategoria = idImagen(c.getImagen());
            List<ProductoCarta> productos = activos.stream()
                    .filter(p -> c.getId().equals(p.getCategoriaId()) && p.isDisponible())
                    .map(p -> new ProductoCarta(TipoProducto.PLATO, p.getId(), p.getNombre(), p.getDescripcion(),
                            p.getPrecio(), p.getPicanteBase(), p.getAlergenos(),
                            p.getImagen() != null ? p.getImagen().id() : fotoCategoria))
                    .toList();
            if (!productos.isEmpty()) {
                secciones.add(new SeccionCarta(c.getId(), c.getNombre(), productos));
            }
        }
        List<ProductoCarta> combosDisponibles = combos.findAllByOrderByNombreAsc().stream()
                .filter(cb -> comboService.motivoNoDisponible(cb, porId) == null)
                .map(cb -> new ProductoCarta(TipoProducto.COMBO, cb.getId(), cb.getNombre(), cb.getDescripcion(),
                        cb.getPrecio(), maxPicante(cb, porId), alergenos(cb, porId), idImagen(cb.getImagen())))
                .toList();
        if (!combosDisponibles.isEmpty()) {
            secciones.add(new SeccionCarta("combos", "Combos", combosDisponibles));
        }
        return secciones;
    }

    private static String idImagen(Imagen imagen) {
        return imagen == null ? null : imagen.id();
    }

    public CotizacionResponse cotizar(CotizacionRequest req) {
        List<ItemCotizado> items = new ArrayList<>();
        boolean todos = true;
        for (ItemCotizar it : req.items()) {
            ItemCotizado c = it.tipo() == TipoProducto.PLATO ? cotizarPlato(it.id()) : cotizarCombo(it.id());
            todos &= c.disponible();
            items.add(c);
        }
        return new CotizacionResponse(items, todos);
    }

    private ItemCotizado cotizarPlato(String id) {
        Plato p = platos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Plato", id));
        String motivo = !p.isActivo() ? "Plato retirado de la carta" : !p.isDisponible() ? "Plato no disponible" : null;
        List<ConsumoInsumo> receta = p.getReceta().stream()
                .map(r -> new ConsumoInsumo(r.insumoId(), r.nombreInsumo(), r.unidad(), r.cantidad())).toList();
        return new ItemCotizado(TipoProducto.PLATO, p.getId(), p.getNombre(), p.getPrecio(), motivo == null, motivo,
                p.getPicanteBase(), p.getAlergenos(), receta);
    }

    private ItemCotizado cotizarCombo(String id) {
        Combo c = combos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Combo", id));
        Map<String, Plato> porId = new LinkedHashMap<>();
        platos.findAllById(c.getItems().stream().map(Combo.ItemCombo::platoId).toList()).forEach(p -> porId.put(p.getId(), p));
        String motivo = comboService.motivoNoDisponible(c, porId);
        Map<String, ConsumoInsumo> receta = new LinkedHashMap<>();
        for (Combo.ItemCombo i : c.getItems()) {
            Plato p = porId.get(i.platoId());
            if (p == null) {
                continue;
            }
            for (Plato.IngredienteReceta r : p.getReceta()) {
                BigDecimal cant = r.cantidad().multiply(BigDecimal.valueOf(i.cantidad()));
                receta.merge(r.insumoId(), new ConsumoInsumo(r.insumoId(), r.nombreInsumo(), r.unidad(), cant),
                        (a, b) -> new ConsumoInsumo(a.insumoId(), a.nombreInsumo(), a.unidad(),
                                a.cantidadPorUnidad().add(b.cantidadPorUnidad())));
            }
        }
        return new ItemCotizado(TipoProducto.COMBO, c.getId(), c.getNombre(), c.getPrecio(), motivo == null, motivo,
                maxPicante(c, porId), alergenos(c, porId), new ArrayList<>(receta.values()));
    }

    private static int maxPicante(Combo c, Map<String, Plato> porId) {
        return c.getItems().stream().map(i -> porId.get(i.platoId())).filter(p -> p != null)
                .mapToInt(Plato::getPicanteBase).max().orElse(0);
    }

    private static Set<String> alergenos(Combo c, Map<String, Plato> porId) {
        Set<String> s = new HashSet<>();
        c.getItems().forEach(i -> {
            Plato p = porId.get(i.platoId());
            if (p != null) {
                s.addAll(p.getAlergenos());
            }
        });
        return s;
    }
}
