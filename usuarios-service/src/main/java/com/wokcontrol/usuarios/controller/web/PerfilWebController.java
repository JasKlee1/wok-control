package com.wokcontrol.usuarios.controller.web;

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
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.service.RolService;
import com.wokcontrol.usuarios.service.UsuarioService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/perfil")
public class PerfilWebController {

    private final UsuarioService usuarios;
    private final RolService roles;

    public PerfilWebController(UsuarioService usuarios, RolService roles) {
        this.usuarios = usuarios;
        this.roles = roles;
    }

    public static class PasswordForm {
        @NotBlank(message = "Ingrese la contraseña actual")
        private String actual;
        @NotBlank(message = "Ingrese la nueva contraseña")
        @Size(min = 8, max = 120, message = "Debe tener entre 8 y 120 caracteres")
        private String nueva;
        @NotBlank(message = "Confirme la nueva contraseña")
        private String confirmacion;

        public String getActual() { return actual; }
        public void setActual(String actual) { this.actual = actual; }
        public String getNueva() { return nueva; }
        public void setNueva(String nueva) { this.nueva = nueva; }
        public String getConfirmacion() { return confirmacion; }
        public void setConfirmacion(String confirmacion) { this.confirmacion = confirmacion; }
    }

    @GetMapping
    public String perfil(Model model) {
        cargar(model, new PasswordForm());
        return "perfil";
    }

    @PostMapping("/datos")
    public String datos(@RequestParam String nombre, @RequestParam(required = false) String email,
            RedirectAttributes flash) {
        try {
            usuarios.actualizarDatosPropios(UsuarioActual.requerido().username(), nombre, email);
            flash.addFlashAttribute("exito", "Datos actualizados. Los cambios en el nombre se verán al iniciar sesión de nuevo.");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/perfil";
    }

    @PostMapping("/password")
    public String password(@Valid @ModelAttribute("cambio") PasswordForm cambio, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (br.hasErrors()) {
            cargar(model, cambio);
            return "perfil";
        }
        try {
            usuarios.cambiarPasswordPropia(UsuarioActual.requerido().username(), cambio.getActual(), cambio.getNueva(),
                    cambio.getConfirmacion());
            flash.addFlashAttribute("exito", "Contraseña actualizada correctamente");
            return "redirect:/perfil";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            cargar(model, new PasswordForm());
            return "perfil";
        }
    }

    private void cargar(Model model, PasswordForm cambio) {
        Usuario u = usuarios.obtenerPorUsername(UsuarioActual.requerido().username());
        model.addAttribute("usuario", u);
        model.addAttribute("rolesUsuario", roles.listar().stream().filter(r -> u.getRoles().contains(r.getCodigo())).toList());
        if (!model.containsAttribute("cambio")) {
            model.addAttribute("cambio", cambio);
        }
    }
}
