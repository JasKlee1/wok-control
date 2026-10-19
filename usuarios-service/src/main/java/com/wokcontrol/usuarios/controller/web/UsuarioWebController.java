package com.wokcontrol.usuarios.controller.web;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.util.UriComponentsBuilder;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.usuarios.client.ProveedoresClient;
import com.wokcontrol.usuarios.dto.UsuarioDtos.ActualizarUsuarioRequest;
import com.wokcontrol.usuarios.dto.UsuarioDtos.CrearUsuarioRequest;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.service.RolService;
import com.wokcontrol.usuarios.service.UsuarioService;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/plataforma/usuarios")
public class UsuarioWebController {

    private final UsuarioService usuarios;
    private final RolService roles;
    private final ProveedoresClient proveedores;

    public UsuarioWebController(UsuarioService usuarios, RolService roles, ProveedoresClient proveedores) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.proveedores = proveedores;
    }

    /** Formulario web: un solo objeto para alta y edición (la contraseña solo aplica al crear). */
    public static class UsuarioForm {
        private String id;
        private String username;
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "Máximo 100 caracteres")
        private String nombre;
        @Email(message = "Correo inválido")
        @Size(max = 120)
        private String email;
        private String password;
        private Set<String> roles = new LinkedHashSet<>();
        private String proveedorId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
        public String getProveedorId() { return proveedorId; }
        public void setProveedorId(String proveedorId) { this.proveedorId = proveedorId; }
        public boolean esNuevo() { return id == null || id.isBlank(); }
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String q, @RequestParam(required = false) String rol,
            @RequestParam(required = false) Boolean activo, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", usuarios.buscar(q, rol, activo, PageRequest.of(Math.max(page, 0), 15)));
        model.addAttribute("roles", roles.listar());
        model.addAttribute("q", q);
        model.addAttribute("rolFiltro", rol);
        model.addAttribute("activoFiltro", activo);
        model.addAttribute("baseUrl", UriComponentsBuilder.fromPath("/plataforma/usuarios")
                .queryParamIfPresent("q", java.util.Optional.ofNullable(q).filter(s -> !s.isBlank()))
                .queryParamIfPresent("rol", java.util.Optional.ofNullable(rol).filter(s -> !s.isBlank()))
                .queryParamIfPresent("activo", java.util.Optional.ofNullable(activo)).build().toUriString());
        return "plataforma/usuarios/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new UsuarioForm());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        Usuario u = usuarios.obtener(id);
        UsuarioForm f = new UsuarioForm();
        f.setId(u.getId());
        f.setUsername(u.getUsername());
        f.setNombre(u.getNombre());
        f.setEmail(u.getEmail());
        f.setRoles(new LinkedHashSet<>(u.getRoles()));
        f.setProveedorId(u.getProveedorId());
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@jakarta.validation.Valid @ModelAttribute("form") UsuarioForm form, BindingResult br,
            Model model, RedirectAttributes flash) {
        if (form.getRoles() == null || form.getRoles().isEmpty()) {
            br.rejectValue("roles", "roles.vacio", "Asigne al menos un rol");
        }
        if (form.esNuevo()) {
            if (form.getUsername() == null || !form.getUsername().trim().toLowerCase().matches("^[a-z0-9._-]{3,40}$")) {
                br.rejectValue("username", "username.invalido", "Use de 3 a 40 caracteres: minúsculas, números, punto o guion");
            }
            if (form.getPassword() == null || form.getPassword().length() < 8) {
                br.rejectValue("password", "password.corta", "La contraseña inicial debe tener al menos 8 caracteres");
            }
        }
        if (br.hasErrors()) {
            return formulario(model, form);
        }
        try {
            String actor = UsuarioActual.responsableActual();
            if (form.esNuevo()) {
                usuarios.crear(new CrearUsuarioRequest(form.getUsername(), form.getNombre(), form.getEmail(),
                        form.getPassword(), form.getRoles(), form.getProveedorId()), actor);
                flash.addFlashAttribute("exito", "Usuario " + form.getUsername() + " creado. Deberá cambiar la contraseña al ingresar.");
            } else {
                usuarios.actualizar(form.getId(), new ActualizarUsuarioRequest(form.getNombre(), form.getEmail(),
                        form.getRoles(), form.getProveedorId()), actor);
                flash.addFlashAttribute("exito", "Usuario actualizado. Los nuevos permisos aplican en su próximo inicio de sesión.");
            }
            return "redirect:/plataforma/usuarios";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    @PostMapping("/{id}/estado")
    public String estado(@PathVariable String id, @RequestParam boolean activo, RedirectAttributes flash) {
        try {
            Usuario u = usuarios.cambiarEstado(id, activo, UsuarioActual.responsableActual());
            flash.addFlashAttribute("exito", "La cuenta " + u.getUsername() + (activo ? " fue activada" : " fue desactivada"));
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/plataforma/usuarios";
    }

    @PostMapping("/{id}/restablecer")
    public String restablecer(@PathVariable String id, @RequestParam String nuevaPassword, RedirectAttributes flash) {
        try {
            usuarios.restablecerPassword(id, nuevaPassword);
            flash.addFlashAttribute("exito", "Contraseña restablecida. El usuario deberá cambiarla al ingresar.");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/plataforma/usuarios/" + id + "/editar";
    }

    private String formulario(Model model, UsuarioForm form) {
        model.addAttribute("form", form);
        model.addAttribute("roles", roles.listar().stream().filter(r -> r.isActivo()).toList());
        model.addAttribute("proveedores", proveedores.listarActivos());
        return "plataforma/usuarios/form";
    }
}
