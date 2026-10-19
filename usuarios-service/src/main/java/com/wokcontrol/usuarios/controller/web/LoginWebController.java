package com.wokcontrol.usuarios.controller.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.security.JwtProperties;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.common.security.WokSecurity;
import com.wokcontrol.usuarios.service.AuthService;
import com.wokcontrol.usuarios.service.CredencialesInvalidasException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Inicio y cierre de sesión web. El JWT se entrega en una cookie HttpOnly (inaccesible para
 * JavaScript) con SameSite=Lax; los formularios se protegen además con token CSRF.
 */
@Controller
public class LoginWebController {

    private final AuthService auth;
    private final JwtProperties props;
    private final AuditoriaPublisher auditoria;

    public LoginWebController(AuthService auth, JwtProperties props, AuditoriaPublisher auditoria) {
        this.auth = auth;
        this.props = props;
        this.auditoria = auditoria;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String continuar, Model model) {
        model.addAttribute("continuar", destinoSeguro(continuar));
        return "login";
    }

    @PostMapping("/login")
    public String autenticar(@RequestParam String username, @RequestParam String password,
            @RequestParam(required = false) String continuar, HttpServletResponse response, Model model) {
        try {
            AuthService.Sesion sesion = auth.autenticar(username, password);
            int maxAge = (int) (props.expirationMinutes() * 60);
            response.addCookie(WokSecurity.cookieToken(props.cookieName(), sesion.token().token(), maxAge, props.cookieSecure()));
            if (sesion.perfil().debeCambiarPassword()) {
                return "redirect:/perfil?cambioObligatorio";
            }
            return "redirect:" + destinoSeguro(continuar);
        } catch (CredencialesInvalidasException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("username", username);
            model.addAttribute("continuar", destinoSeguro(continuar));
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        UsuarioActual.opcional().ifPresent(u -> auditoria.registrar("CERRAR_SESION", "Usuario", u.username(), null));
        WokSecurity.borrarCookie(response, props.cookieName(), props.cookieSecure());
        return "redirect:/login?salir";
    }

    @GetMapping("/acceso-denegado")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accesoDenegado() {
        return "acceso-denegado";
    }

    @GetMapping("/docs-api")
    public String documentacion() {
        return "docs-api";
    }

    /** Evita redirecciones abiertas: solo rutas internas relativas. */
    static String destinoSeguro(String continuar) {
        if (continuar == null || continuar.isBlank() || !continuar.startsWith("/") || continuar.startsWith("//")
                || continuar.contains("\\") || continuar.startsWith("/login") || continuar.startsWith("/logout")) {
            return "/dashboard";
        }
        return continuar;
    }
}
