package com.wokcontrol.pedidos.controller.web;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.wokcontrol.common.security.JwtProperties;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.common.security.WokSecurity;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.service.SesionMesaService;
import com.wokcontrol.pedidos.service.SesionMesaService.MesaNoDisponibleException;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionAbierta;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionVencidaException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * v2 · Épica 7 · Cliente en el salón. T002 (HU-036) y T004 (HU-033):
 * <ul>
 * <li>{@code GET /cliente/mesa/{codigo}}: lo abre el QR. Sin usuario ni contraseña: crea la sesión de
 * mesa y deja el token en la cookie HttpOnly; si la mesa no está disponible, no abre sesión.</li>
 * <li>{@code GET /cliente/carta}: carta con foto, precio y solo los platos disponibles.</li>
 * </ul>
 */
@Controller
public class ClienteMesaWebController {

    private static final Locale CO = Locale.of("es", "CO");

    private final SesionMesaService sesiones;
    private final MenuClient menu;
    private final JwtProperties jwt;

    public ClienteMesaWebController(SesionMesaService sesiones, MenuClient menu, JwtProperties jwt) {
        this.sesiones = sesiones;
        this.menu = menu;
        this.jwt = jwt;
    }

    public record PlatoVista(String nombre, String descripcion, String precio, int picante, Set<String> alergenos,
            String imagenId, boolean combo) {
    }

    public record SeccionVista(String nombre, List<PlatoVista> platos) {
    }

    @GetMapping("/cliente/mesa/{codigo}")
    public String entrar(@PathVariable String codigo, HttpServletResponse res, Model model) {
        try {
            SesionAbierta s = sesiones.abrir(codigo);
            int segundos = (int) Math.max(60, Duration.between(Instant.now(), s.expiraEn()).getSeconds());
            res.addCookie(WokSecurity.cookieToken(jwt.cookieName(), s.token(), segundos, jwt.cookieSecure()));
            return "redirect:/cliente/carta";
        } catch (MesaNoDisponibleException ex) {
            res.setStatus(HttpStatus.NOT_FOUND.value());
            return aviso(model, "bi-exclamation-octagon", "Mesa no disponible", ex.getMessage());
        }
    }

    @GetMapping("/cliente/carta")
    public String carta(@AuthenticationPrincipal Jwt token, HttpServletResponse res, Model model) {
        try {
            var sesion = sesiones.validar(token.getClaimAsString(TokenService.CLAIM_SESION),
                    token.getClaimAsString(TokenService.CLAIM_MESA));
            model.addAttribute("mesaNumero", sesion.getMesaNumero());
        } catch (SesionVencidaException ex) {
            WokSecurity.borrarCookie(res, jwt.cookieName(), jwt.cookieSecure());
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            return aviso(model, "bi-qr-code-scan", "Tu sesión terminó",
                    "La cuenta de esta mesa ya se cerró. Escanea el QR de tu mesa para volver a ver la carta.");
        }
        List<SeccionVista> secciones = menu.carta().stream()
                .map(s -> new SeccionVista(s.categoria(), s.productos().stream()
                        .map(p -> new PlatoVista(p.nombre(), p.descripcion(), dinero(p.precio()), p.picanteBase(),
                                p.alergenos() == null ? Set.of() : p.alergenos(), p.imagenId(), "COMBO".equals(p.tipo())))
                        .toList()))
                .filter(s -> !s.platos().isEmpty())
                .toList();
        model.addAttribute("secciones", secciones);
        return "cliente/carta";
    }

    private static String aviso(Model model, String icono, String titulo, String mensaje) {
        model.addAttribute("icono", icono);
        model.addAttribute("titulo", titulo);
        model.addAttribute("mensaje", mensaje);
        return "cliente/aviso";
    }

    static String dinero(BigDecimal valor) {
        return valor == null ? "" : String.format(CO, "$%,d", valor.setScale(0, java.math.RoundingMode.HALF_UP).longValue());
    }
}
