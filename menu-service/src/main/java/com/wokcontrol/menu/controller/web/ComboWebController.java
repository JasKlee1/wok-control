package com.wokcontrol.menu.controller.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
import com.wokcontrol.menu.dto.MenuDtos.ComboRequest;
import com.wokcontrol.menu.dto.MenuDtos.ItemComboRequest;
import com.wokcontrol.menu.entity.Combo;
import com.wokcontrol.menu.service.ComboService;
import com.wokcontrol.menu.service.PlatoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/menu/combos")
public class ComboWebController {

    private final ComboService combos;
    private final PlatoService platos;

    public ComboWebController(ComboService combos, PlatoService platos) {
        this.combos = combos;
        this.platos = platos;
    }

    public static class ItemForm {
        private String platoId;
        private Integer cantidad = 1;

        public String getPlatoId() { return platoId; }
        public void setPlatoId(String platoId) { this.platoId = platoId; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    }

    public static class ComboForm {
        private String id;
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80)
        private String nombre;
        @Size(max = 300)
        private String descripcion;
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
        @DecimalMax(value = "10000000")
        private BigDecimal precio;
        private boolean disponible = true;
        private List<ItemForm> items = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public BigDecimal getPrecio() { return precio; }
        public void setPrecio(BigDecimal precio) { this.precio = precio; }
        public boolean isDisponible() { return disponible; }
        public void setDisponible(boolean disponible) { this.disponible = disponible; }
        public List<ItemForm> getItems() { return items; }
        public void setItems(List<ItemForm> items) { this.items = items; }
        public boolean esNuevo() { return id == null || id.isBlank(); }

        List<ItemComboRequest> itemsValidos() {
            List<ItemComboRequest> r = new ArrayList<>();
            if (items != null) {
                for (ItemForm i : items) {
                    if (i != null && i.getPlatoId() != null && !i.getPlatoId().isBlank()) {
                        r.add(new ItemComboRequest(i.getPlatoId(), i.getCantidad() == null ? 1 : i.getCantidad()));
                    }
                }
            }
            return r;
        }
    }

    @GetMapping
    public String lista(Model model) {
        model.addAttribute("combos", combos.listar(false).stream().map(combos::aRespuesta).toList());
        return "menu/combos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        ComboForm f = new ComboForm();
        f.getItems().add(new ItemForm());
        return formulario(model, f);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        Combo c = combos.obtener(id);
        ComboForm f = new ComboForm();
        f.setId(c.getId());
        f.setNombre(c.getNombre());
        f.setDescripcion(c.getDescripcion());
        f.setPrecio(c.getPrecio());
        f.setDisponible(c.isDisponible());
        for (Combo.ItemCombo i : c.getItems()) {
            ItemForm it = new ItemForm();
            it.setPlatoId(i.platoId());
            it.setCantidad(i.cantidad());
            f.getItems().add(it);
        }
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("form") ComboForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        List<ItemComboRequest> items = form.itemsValidos();
        if (items.isEmpty()) {
            br.reject("items.vacio", "Agregue al menos un plato al combo");
        }
        for (ItemComboRequest i : items) {
            if (i.cantidad() < 1 || i.cantidad() > 20) {
                br.reject("items.cantidad", "Las cantidades deben estar entre 1 y 20");
            }
        }
        if (br.hasErrors()) {
            return formulario(model, form);
        }
        try {
            ComboRequest req = new ComboRequest(form.getNombre(), form.getDescripcion(), items, form.getPrecio(), form.isDisponible());
            Combo c = form.esNuevo() ? combos.crear(req) : combos.actualizar(form.getId(), req);
            flash.addFlashAttribute("exito", "Combo \"" + c.getNombre() + "\" guardado");
            return "redirect:/menu/combos";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    @PostMapping("/{id}/disponibilidad")
    public String disponibilidad(@PathVariable String id, @RequestParam boolean disponible, RedirectAttributes flash) {
        combos.cambiarDisponibilidad(id, disponible);
        flash.addFlashAttribute("exito", disponible ? "Combo habilitado" : "Combo marcado como no disponible");
        return "redirect:/menu/combos";
    }

    @PostMapping("/{id}/retirar")
    public String retirar(@PathVariable String id, RedirectAttributes flash) {
        combos.retirar(id);
        flash.addFlashAttribute("exito", "Combo retirado de la carta");
        return "redirect:/menu/combos";
    }

    private String formulario(Model model, ComboForm form) {
        model.addAttribute("form", form);
        model.addAttribute("platos", platos.buscar(null, null, null, false));
        return "menu/combos/form";
    }
}
