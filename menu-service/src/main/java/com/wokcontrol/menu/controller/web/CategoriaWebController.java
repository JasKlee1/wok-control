package com.wokcontrol.menu.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.menu.dto.MenuDtos.CategoriaRequest;
import com.wokcontrol.menu.entity.Categoria;
import com.wokcontrol.menu.service.CategoriaService;
import com.wokcontrol.menu.service.PlatoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/menu/categorias")
public class CategoriaWebController {

    private final CategoriaService categorias;
    private final PlatoService platos;

    public CategoriaWebController(CategoriaService categorias, PlatoService platos) {
        this.categorias = categorias;
        this.platos = platos;
    }

    public static class CategoriaForm {
        private String id;
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "Máximo 60 caracteres")
        private String nombre;
        @Size(max = 200)
        private String descripcion;
        @Min(value = 0, message = "No puede ser negativo")
        @Max(999)
        private int orden;
        private boolean activa = true;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public int getOrden() { return orden; }
        public void setOrden(int orden) { this.orden = orden; }
        public boolean isActiva() { return activa; }
        public void setActiva(boolean activa) { this.activa = activa; }
        public boolean esNuevo() { return id == null || id.isBlank(); }
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String editar, Model model) {
        CategoriaForm f = new CategoriaForm();
        if (editar != null) {
            Categoria c = categorias.obtener(editar);
            f.setId(c.getId());
            f.setNombre(c.getNombre());
            f.setDescripcion(c.getDescripcion());
            f.setOrden(c.getOrden());
            f.setActiva(c.isActiva());
        }
        return vista(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("form") CategoriaForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (br.hasErrors()) {
            return vista(model, form);
        }
        try {
            CategoriaRequest req = new CategoriaRequest(form.getNombre(), form.getDescripcion(), form.getOrden(), form.isActiva());
            if (form.esNuevo()) {
                categorias.crear(req);
            } else {
                categorias.actualizar(form.getId(), req);
            }
            flash.addFlashAttribute("exito", "Categoría \"" + form.getNombre() + "\" guardada");
            return "redirect:/menu/categorias";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return vista(model, form);
        }
    }

    private String vista(Model model, CategoriaForm form) {
        model.addAttribute("form", form);
        model.addAttribute("categorias", categorias.listar());
        if (!form.esNuevo()) {
            model.addAttribute("categoriaEditada", categorias.obtener(form.getId()));
        }
        java.util.Map<String, Long> conteo = new java.util.HashMap<>();
        platos.buscar(null, null, null, false).forEach(p -> conteo.merge(p.getCategoriaId(), 1L, Long::sum));
        model.addAttribute("conteo", conteo);
        return "menu/categorias";
    }
}
