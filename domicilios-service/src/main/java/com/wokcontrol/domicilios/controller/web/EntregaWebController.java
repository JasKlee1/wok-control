package com.wokcontrol.domicilios.controller.web;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.domicilios.client.QrRutaClient;
import com.wokcontrol.domicilios.dto.DomicilioDtos.CobroRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.MetodoCobro;
import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.service.DomicilioService;

/**
 * v2 · HU-049/050/052: «Mis entregas», la pantalla del domiciliario en el celular. Solo muestra y
 * opera lo que tiene asignado (el servicio lo comprueba en cada acción, AC-051.1).
 */
@Controller
@RequestMapping("/mis-entregas")
public class EntregaWebController {

    private final DomicilioService servicio;
    private final QrRutaClient qr;

    public EntregaWebController(DomicilioService servicio, QrRutaClient qr) {
        this.servicio = servicio;
        this.qr = qr;
    }

    @GetMapping
    public String lista(Model model) {
        java.util.List<Domicilio> entregas = servicio.mios(UsuarioActual.requerido().responsable());
        java.util.Map<String, String> rutas = new java.util.HashMap<>();
        entregas.forEach(d -> rutas.put(d.getId(), servicio.urlRuta(d)));
        model.addAttribute("entregas", entregas);
        model.addAttribute("rutas", rutas);
        model.addAttribute("metodos", MetodoCobro.values());
        model.addAttribute("qrProveedor", qr.proveedor());
        return "entregas/lista";
    }

    @PostMapping("/{id}/en-camino")
    public String enCamino(@PathVariable String id, RedirectAttributes flash) {
        try {
            Domicilio d = servicio.enCamino(id, UsuarioActual.requerido());
            flash.addFlashAttribute("exito", "Domicilio " + d.getNumero() + " en camino. ¡Buen viaje!");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/mis-entregas";
    }

    @PostMapping("/{id}/entregar")
    public String entregar(@PathVariable String id, @RequestParam(required = false) MetodoCobro metodo,
            @RequestParam(required = false) BigDecimal monto, @RequestParam(required = false) String referencia,
            RedirectAttributes flash) {
        try {
            Domicilio d = servicio.entregar(id, new CobroRequest(metodo, monto, referencia), UsuarioActual.requerido());
            flash.addFlashAttribute("exito", "Domicilio " + d.getNumero() + " entregado · comprobante " + d.getCobro().facturaNumero()
                    + (d.getCobro().cambio().signum() > 0 ? " · devuelva $" + d.getCobro().cambio().toPlainString() + " de cambio" : ""));
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/mis-entregas";
    }

    @PostMapping("/{id}/no-entregado")
    public String noEntregado(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes flash) {
        try {
            Domicilio d = servicio.noEntregado(id, motivo, UsuarioActual.requerido());
            flash.addFlashAttribute("exito", "Domicilio " + d.getNumero() + " marcado como no entregado; vuelva con el pedido al restaurante");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/mis-entregas";
    }
}
