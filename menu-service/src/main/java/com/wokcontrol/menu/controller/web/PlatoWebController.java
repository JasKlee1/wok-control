package com.wokcontrol.menu.controller.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.menu.client.InventarioClient;
import com.wokcontrol.menu.dto.MenuDtos;
import com.wokcontrol.menu.dto.MenuDtos.IngredienteRequest;
import com.wokcontrol.menu.dto.MenuDtos.PlatoRequest;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.service.CategoriaService;
import com.wokcontrol.menu.service.PlatoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/menu/platos")
public class PlatoWebController {

    private final PlatoService platos;
    private final CategoriaService categorias;
    private final InventarioClient inventario;

    public PlatoWebController(PlatoService platos, CategoriaService categorias, InventarioClient inventario) {
        this.platos = platos;
        this.categorias = categorias;
        this.inventario = inventario;
    }

    public static class IngredienteForm {
        private String insumoId;
        private BigDecimal cantidad;

        public String getInsumoId() { return insumoId; }
        public void setInsumoId(String insumoId) { this.insumoId = insumoId; }
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    }

    public static class PlatoForm {
        private String id;
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "Máximo 80 caracteres")
        private String nombre;
        @Size(max = 300, message = "Máximo 300 caracteres")
        private String descripcion;
        @NotBlank(message = "Seleccione la categoría")
        private String categoriaId;
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
        @DecimalMax(value = "10000000", message = "Precio fuera de rango")
        private BigDecimal precio;
        private boolean disponible = true;
        @Min(value = 0, message = "Mínimo 0")
        @Max(value = 3, message = "Máximo 3")
        private int picanteBase;
        private Set<String> alergenos = new LinkedHashSet<>();
        @Min(value = 1, message = "Mínimo 1 minuto")
        @Max(value = 240, message = "Máximo 240 minutos")
        private Integer tiempoPreparacionMin;
        private List<IngredienteForm> receta = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public String getCategoriaId() { return categoriaId; }
        public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
        public BigDecimal getPrecio() { return precio; }
        public void setPrecio(BigDecimal precio) { this.precio = precio; }
        public boolean isDisponible() { return disponible; }
        public void setDisponible(boolean disponible) { this.disponible = disponible; }
        public int getPicanteBase() { return picanteBase; }
        public void setPicanteBase(int picanteBase) { this.picanteBase = picanteBase; }
        public Set<String> getAlergenos() { return alergenos; }
        public void setAlergenos(Set<String> alergenos) { this.alergenos = alergenos; }
        public Integer getTiempoPreparacionMin() { return tiempoPreparacionMin; }
        public void setTiempoPreparacionMin(Integer t) { this.tiempoPreparacionMin = t; }
        public List<IngredienteForm> getReceta() { return receta; }
        public void setReceta(List<IngredienteForm> receta) { this.receta = receta; }
        public boolean esNuevo() { return id == null || id.isBlank(); }

        PlatoRequest aRequest() {
            List<IngredienteRequest> ing = new ArrayList<>();
            if (receta != null) {
                for (IngredienteForm f : receta) {
                    if (f != null && f.getInsumoId() != null && !f.getInsumoId().isBlank()) {
                        ing.add(new IngredienteRequest(f.getInsumoId(), f.getCantidad()));
                    }
                }
            }
            return new PlatoRequest(nombre, descripcion, categoriaId, precio, disponible, picanteBase, alergenos,
                    tiempoPreparacionMin, ing);
        }
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String q, @RequestParam(required = false) String categoriaId,
            @RequestParam(required = false) Boolean disponible, Model model) {
        model.addAttribute("platos", platos.buscar(q, categoriaId, disponible, false));
        model.addAttribute("categorias", categorias.listar());
        model.addAttribute("nombresCategorias", platos.nombresCategorias());
        model.addAttribute("fotosCategorias", platos.fotosCategorias());
        model.addAttribute("q", q);
        model.addAttribute("categoriaFiltro", categoriaId);
        model.addAttribute("disponibleFiltro", disponible);
        return "menu/platos/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable String id, Model model) {
        Plato p = platos.obtener(id);
        model.addAttribute("plato", p);
        model.addAttribute("categoria", platos.nombresCategorias().get(p.getCategoriaId()));
        model.addAttribute("fotoCategoria", platos.fotosCategorias().get(p.getCategoriaId()));
        return "menu/platos/detalle";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new PlatoForm());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        Plato p = platos.obtener(id);
        PlatoForm f = new PlatoForm();
        f.setId(p.getId());
        f.setNombre(p.getNombre());
        f.setDescripcion(p.getDescripcion());
        f.setCategoriaId(p.getCategoriaId());
        f.setPrecio(p.getPrecio());
        f.setDisponible(p.isDisponible());
        f.setPicanteBase(p.getPicanteBase());
        f.setAlergenos(new LinkedHashSet<>(p.getAlergenos()));
        f.setTiempoPreparacionMin(p.getTiempoPreparacionMin());
        for (Plato.IngredienteReceta r : p.getReceta()) {
            IngredienteForm i = new IngredienteForm();
            i.setInsumoId(r.insumoId());
            i.setCantidad(r.cantidad());
            f.getReceta().add(i);
        }
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("form") PlatoForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (form.getReceta() != null) {
            for (int i = 0; i < form.getReceta().size(); i++) {
                IngredienteForm ing = form.getReceta().get(i);
                if (ing != null && ing.getInsumoId() != null && !ing.getInsumoId().isBlank()
                        && (ing.getCantidad() == null || ing.getCantidad().signum() <= 0)) {
                    br.rejectValue("receta[" + i + "].cantidad", "cantidad", "Cantidad positiva");
                }
            }
        }
        if (br.hasErrors()) {
            return formulario(model, form);
        }
        try {
            Plato p = form.esNuevo() ? platos.crear(form.aRequest()) : platos.actualizar(form.getId(), form.aRequest());
            flash.addFlashAttribute("exito", "Plato \"" + p.getNombre() + "\" guardado");
            return "redirect:/menu/platos/" + p.getId();
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    @PostMapping("/{id}/disponibilidad")
    public String disponibilidad(@PathVariable String id, @RequestParam boolean disponible, RedirectAttributes flash) {
        try {
            Plato p = platos.cambiarDisponibilidad(id, disponible);
            flash.addFlashAttribute("exito", p.getNombre() + (disponible ? " está disponible" : " quedó marcado como agotado"));
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/menu/platos";
    }

    @PostMapping("/{id}/retirar")
    public String retirar(@PathVariable String id, RedirectAttributes flash) {
        platos.retirar(id);
        flash.addFlashAttribute("exito", "Plato retirado de la carta. Se conserva en el historial de pedidos.");
        return "redirect:/menu/platos";
    }

    private String formulario(Model model, PlatoForm form) {
        model.addAttribute("form", form);
        model.addAttribute("categorias", categorias.listar().stream().filter(c -> c.isActiva()).toList());
        model.addAttribute("alergenos", MenuDtos.ALERGENOS);
        try {
            model.addAttribute("insumos", inventario.insumosActivos());
        } catch (WokException ex) {
            model.addAttribute("insumos", List.of());
            model.addAttribute("advertencia", "No fue posible consultar inventario-service: la receta no podrá modificarse ahora. "
                    + ex.getMessage());
        }
        return "menu/platos/form";
    }
}
