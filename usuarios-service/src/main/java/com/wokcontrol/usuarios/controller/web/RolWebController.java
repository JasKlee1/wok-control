package com.wokcontrol.usuarios.controller.web;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.usuarios.dto.RolDtos.RolRequest;
import com.wokcontrol.usuarios.entity.Rol;
import com.wokcontrol.usuarios.service.RolService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/plataforma/roles")
public class RolWebController {

    private final RolService roles;

    public RolWebController(RolService roles) {
        this.roles = roles;
    }

    public static class RolForm {
        private boolean nuevo = true;
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "Use mayúsculas, números y guion bajo (3 a 40)")
        private String codigo;
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60)
        private String nombre;
        @Size(max = 250)
        private String descripcion;
        private Set<String> permisos = new LinkedHashSet<>();
        private boolean activo = true;

        public boolean isNuevo() { return nuevo; }
        public void setNuevo(boolean nuevo) { this.nuevo = nuevo; }
        public String getCodigo() { return codigo; }
        public void setCodigo(String codigo) { this.codigo = codigo; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public Set<String> getPermisos() { return permisos; }
        public void setPermisos(Set<String> permisos) { this.permisos = permisos; }
        public boolean isActivo() { return activo; }
        public void setActivo(boolean activo) { this.activo = activo; }
    }

    @GetMapping
    public String lista(Model model) {
        model.addAttribute("roles", roles.listar());
        model.addAttribute("permisos", new TreeMap<>(Permisos.DESCRIPCIONES));
        return "plataforma/roles/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new RolForm());
    }

    @GetMapping("/{codigo}/editar")
    public String editar(@PathVariable String codigo, Model model, RedirectAttributes flash) {
        Rol r = roles.obtener(codigo);
        if (r.isSistema()) {
            flash.addFlashAttribute("info", "Los roles del sistema son de solo lectura. Cree un rol personalizado si necesita otra combinación.");
            return "redirect:/plataforma/roles";
        }
        RolForm f = new RolForm();
        f.setNuevo(false);
        f.setCodigo(r.getCodigo());
        f.setNombre(r.getNombre());
        f.setDescripcion(r.getDescripcion());
        f.setPermisos(new LinkedHashSet<>(r.getPermisos()));
        f.setActivo(r.isActivo());
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("form") RolForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (form.getPermisos() == null || form.getPermisos().isEmpty()) {
            br.rejectValue("permisos", "permisos.vacio", "Seleccione al menos un permiso");
        }
        if (br.hasErrors()) {
            return formulario(model, form);
        }
        try {
            RolRequest req = new RolRequest(form.getCodigo(), form.getNombre(), form.getDescripcion(), form.getPermisos(),
                    form.isActivo());
            if (form.isNuevo()) {
                roles.crear(req);
            } else {
                roles.actualizar(form.getCodigo(), req);
            }
            flash.addFlashAttribute("exito", "Rol " + form.getCodigo() + " guardado");
            return "redirect:/plataforma/roles";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    private String formulario(Model model, RolForm form) {
        model.addAttribute("form", form);
        TreeMap<String, String> catalogo = new TreeMap<>(Permisos.DESCRIPCIONES);
        catalogo.remove(Permisos.SISTEMA);
        model.addAttribute("permisos", catalogo);
        return "plataforma/roles/form";
    }
}
