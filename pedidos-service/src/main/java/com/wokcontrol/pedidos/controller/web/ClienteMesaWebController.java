package com.wokcontrol.pedidos.controller.web;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.JwtProperties;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.common.security.WokSecurity;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.service.PedidoClienteService;
import com.wokcontrol.pedidos.service.PedidoClienteService.Carrito;
import com.wokcontrol.pedidos.service.PedidoClienteService.CuentaSolicitada;
import com.wokcontrol.pedidos.service.SesionMesaService;
import com.wokcontrol.pedidos.service.SesionMesaService.MesaNoDisponibleException;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionAbierta;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionVencidaException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * v2 · Épica 7 · Cliente en el salón, desde el celular y con la sesión de mesa del QR:
 * <ul>
 * <li>T002 (HU-036) y T004 (HU-033): {@code GET /cliente/mesa/{codigo}} abre la sesión sin usuario ni
 * contraseña; {@code GET /cliente/carta} muestra solo los platos disponibles.</li>
 * <li>T006 (HU-032): {@code /cliente/carrito/**} arma el pedido antes de enviarlo.</li>
 * <li>T008 (HU-034): {@code POST /cliente/pedido/enviar} y {@code GET /cliente/pedido} para seguirlo.</li>
 * <li>T012 (HU-038): {@code GET /cliente/estado} avisa al celular cuando algo cambió (plato agotado,
 * avance de cocina, rechazo).</li>
 * <li>T014 (HU-035): {@code POST /cliente/cuenta} pide la cuenta.</li>
 * </ul>
 * Toda ruta valida contra la base que la sesión siga ACTIVA y sea de la mesa del token (AC-036.2).
 */
@Controller
public class ClienteMesaWebController {

    private static final Locale CO = Locale.of("es", "CO");

    private final SesionMesaService sesiones;
    private final PedidoClienteService cliente;
    private final MenuClient menu;
    private final JwtProperties jwt;

    public ClienteMesaWebController(SesionMesaService sesiones, PedidoClienteService cliente, MenuClient menu,
            JwtProperties jwt) {
        this.sesiones = sesiones;
        this.cliente = cliente;
        this.menu = menu;
        this.jwt = jwt;
    }

    public record PlatoVista(String id, String tipo, String nombre, String descripcion, String precio, int picante,
            Set<String> alergenos, String imagenId, boolean combo) {
    }

    public record SeccionVista(String nombre, List<PlatoVista> platos) {
    }

    public record LineaVista(String id, String nombre, int cantidad, String notas, String precio, String subtotal,
            boolean agotado, String motivoAgotado) {
    }

    public record PlatoEnviadoVista(String id, String nombre, int cantidad, String notas, String estado, String clase) {
    }

    public record PedidoVista(String id, String numero, String estado, String clase, boolean rechazado,
            String motivoRechazo, String total, List<PlatoEnviadoVista> platos) {
    }

    // ------------------------------------------------------------------ entrada por QR y carta

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
    public String carta(@AuthenticationPrincipal Jwt token, Model model) {
        SesionMesa sesion = sesion(token);
        model.addAttribute("mesaNumero", sesion.getMesaNumero());
        List<SeccionVista> secciones = menu.carta().stream()
                .map(s -> new SeccionVista(s.categoria(), s.productos().stream()
                        .map(p -> new PlatoVista(p.id(), p.tipo(), p.nombre(), p.descripcion(), dinero(p.precio()),
                                p.picanteBase(), p.alergenos() == null ? Set.of() : p.alergenos(), p.imagenId(),
                                "COMBO".equals(p.tipo())))
                        .toList()))
                .filter(s -> !s.platos().isEmpty())
                .toList();
        model.addAttribute("secciones", secciones);
        resumenCarrito(sesion, model);
        return "cliente/carta";
    }

    // ------------------------------------------------------------------ HU-032 · pedido sin enviar

    @PostMapping("/cliente/carrito")
    public String agregar(@AuthenticationPrincipal Jwt token, @RequestParam String tipo, @RequestParam String productoId,
            @RequestParam(defaultValue = "1") int cantidad, RedirectAttributes flash) {
        SesionMesa sesion = sesion(token);
        try {
            SesionMesa s = cliente.agregar(sesion, tipo, productoId, cantidad);
            String nombre = s.getCarrito().stream().filter(l -> l.getProductoId().equals(productoId))
                    .map(SesionMesa.LineaCarrito::getNombre).findFirst().orElse("Plato");
            flash.addFlashAttribute("exito", nombre + " agregado a tu pedido");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cliente/carta";
    }

    @PostMapping("/cliente/carrito/{lineaId}")
    public String cambiar(@AuthenticationPrincipal Jwt token, @PathVariable String lineaId, @RequestParam int cantidad,
            @RequestParam(required = false) String notas, RedirectAttributes flash) {
        SesionMesa sesion = sesion(token);
        try {
            cliente.cambiar(sesion, lineaId, cantidad, notas);
            flash.addFlashAttribute("exito", "Pedido actualizado");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cliente/pedido";
    }

    @PostMapping("/cliente/carrito/{lineaId}/quitar")
    public String quitar(@AuthenticationPrincipal Jwt token, @PathVariable String lineaId, RedirectAttributes flash) {
        SesionMesa sesion = sesion(token);
        try {
            cliente.quitar(sesion, lineaId);
            flash.addFlashAttribute("exito", "Plato quitado de tu pedido");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cliente/pedido";
    }

    /** AC-032.2: un plato de un pedido ya enviado no se quita desde el celular. */
    @PostMapping("/cliente/pedidos/{pedidoId}/items/{itemId}/quitar")
    public String quitarEnviado(@AuthenticationPrincipal Jwt token, @PathVariable String pedidoId,
            @PathVariable String itemId, HttpServletResponse res, Model model, RedirectAttributes flash) {
        SesionMesa sesion = sesion(token);
        try {
            cliente.modificarEnviado(sesion, pedidoId);
        } catch (OperacionNoPermitidaException ex) {
            // AC-036.1: 403 y ningún dato del pedido de otra mesa
            res.setStatus(HttpStatus.FORBIDDEN.value());
            model.addAttribute("mesaNumero", sesion.getMesaNumero());
            return aviso(model, "bi-shield-lock", "Acceso no permitido", ex.getMessage());
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cliente/pedido";
    }

    // ------------------------------------------------------------------ HU-034 · enviar y seguir

    @GetMapping("/cliente/pedido")
    public String pedido(@AuthenticationPrincipal Jwt token, Model model) {
        SesionMesa sesion = sesion(token);
        model.addAttribute("mesaNumero", sesion.getMesaNumero());
        try {
            Carrito c = cliente.carrito(sesion);
            model.addAttribute("lineas", c.lineas().stream()
                    .map(l -> new LineaVista(l.id(), l.nombre(), l.cantidad(), l.notas(), dinero(l.precioUnitario()),
                            dinero(l.subtotal()), l.agotado(), l.motivoAgotado()))
                    .toList());
            model.addAttribute("totalCarrito", dinero(c.total()));
            model.addAttribute("hayAgotados", c.hayAgotados());
        } catch (WokException ex) {
            model.addAttribute("lineas", List.of());
            model.addAttribute("totalCarrito", dinero(BigDecimal.ZERO));
            model.addAttribute("hayAgotados", false);
            model.addAttribute("error", "No pudimos revisar la carta en este momento: " + ex.getMessage());
        }
        List<Pedido> enviados = cliente.pedidosDeSesion(sesion);
        model.addAttribute("pedidos", enviados.stream().map(ClienteMesaWebController::vista).toList());
        model.addAttribute("cuentaSolicitada", cliente.cuentaSolicitada(sesion));
        model.addAttribute("puedePedirCuenta", enviados.stream().anyMatch(p -> EstadoPedido.ACTIVOS.contains(p.getEstado())));
        model.addAttribute("huella", cliente.huella(sesion));
        return "cliente/pedido";
    }

    @PostMapping("/cliente/pedido/enviar")
    public String enviar(@AuthenticationPrincipal Jwt token, RedirectAttributes flash) {
        SesionMesa sesion = sesion(token);
        try {
            Pedido p = cliente.enviar(sesion);
            flash.addFlashAttribute("exito", "Pedido " + p.getNumero() + " enviado. Tu mesero lo confirmará en un momento.");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cliente/pedido";
    }

    /** El celular consulta esta huella cada pocos segundos y recarga «Mi pedido» si cambió. */
    @GetMapping(value = "/cliente/estado", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> estado(@AuthenticationPrincipal Jwt token) {
        return Map.of("huella", cliente.huella(sesion(token)));
    }

    // ------------------------------------------------------------------ HU-035 · pedir la cuenta

    @PostMapping("/cliente/cuenta")
    public String cuenta(@AuthenticationPrincipal Jwt token, RedirectAttributes flash) {
        SesionMesa sesion = sesion(token);
        try {
            CuentaSolicitada c = cliente.pedirCuenta(sesion);
            flash.addFlashAttribute("exito", "Pediste la cuenta: " + dinero(c.total()) + ". El cajero ya la está preparando."
                    + (c.platosSinEntregar() > 0 ? " Aún hay " + c.platosSinEntregar() + " plato(s) por llegar a tu mesa." : ""));
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cliente/pedido";
    }

    // ------------------------------------------------------------------ sesión vencida

    /** AC-036.2: con la cuenta cerrada, cualquier ruta del cliente pide escanear el QR de nuevo. */
    @ExceptionHandler(SesionVencidaException.class)
    public ModelAndView sesionVencida(HttpServletResponse res) {
        WokSecurity.borrarCookie(res, jwt.cookieName(), jwt.cookieSecure());
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        ModelAndView mv = new ModelAndView("cliente/aviso");
        mv.setStatus(HttpStatus.UNAUTHORIZED);
        mv.addObject("icono", "bi-qr-code-scan");
        mv.addObject("titulo", "Tu sesión terminó");
        mv.addObject("mensaje", "La cuenta de esta mesa ya se cerró. Escanea el QR de tu mesa para volver a ver la carta.");
        return mv;
    }

    // ------------------------------------------------------------------ apoyo

    private SesionMesa sesion(Jwt token) {
        return sesiones.validar(token.getClaimAsString(TokenService.CLAIM_SESION), token.getClaimAsString(TokenService.CLAIM_MESA));
    }

    private static void resumenCarrito(SesionMesa s, Model model) {
        int unidades = s.getCarrito().stream().mapToInt(SesionMesa.LineaCarrito::getCantidad).sum();
        BigDecimal total = s.getCarrito().stream().map(SesionMesa.LineaCarrito::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("unidadesCarrito", unidades);
        model.addAttribute("totalCarrito", dinero(total));
    }

    private static PedidoVista vista(Pedido p) {
        String clase = switch (p.getEstado()) {
            case POR_CONFIRMAR -> "espera";
            case RECHAZADO, CANCELADO -> "rechazo";
            case LISTO, ENTREGADO, PAGADO -> "listo";
            default -> "cocina";
        };
        return new PedidoVista(p.getId(), p.getNumero(), p.getEstado().etiqueta(), clase,
                p.getEstado() == EstadoPedido.RECHAZADO, p.getMotivoRechazo(), dinero(p.getTotal()),
                p.getItems().stream().map(i -> {
                    String estado = PedidoClienteService.estadoPlato(p, i);
                    String c = switch (estado) {
                        case "Por confirmar" -> "espera";
                        case "Rechazado", "Cancelado" -> "rechazo";
                        case "Listo", "Entregado" -> "listo";
                        default -> "cocina";
                    };
                    return new PlatoEnviadoVista(i.getId(), i.getNombre(), i.getCantidad(), i.getNotas(), estado, c);
                }).toList());
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
