package com.wokcontrol.pedidos.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.pedidos.client.InventarioClient;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.service.PedidoService;

/**
 * Pantalla de cocina: comandas por orden de llegada, avance de preparación, alertas de inventario
 * y reporte de insumos agotados.
 */
@Controller
@RequestMapping("/cocina")
public class CocinaWebController {

    static final int MINUTOS_URGENTE = 20;

    private final PedidoService pedidos;
    private final InventarioClient inventario;

    public CocinaWebController(PedidoService pedidos, InventarioClient inventario) {
        this.pedidos = pedidos;
        this.inventario = inventario;
    }

    @GetMapping
    public String tablero(Model model) {
        model.addAttribute("comandas", pedidos.comandasCocina());
        model.addAttribute("minutosUrgente", MINUTOS_URGENTE);
        try {
            model.addAttribute("alertas", inventario.alertas());
            model.addAttribute("insumos", inventario.insumos());
        } catch (WokException ex) {
            model.addAttribute("alertasError", "Alertas de inventario no disponibles: " + ex.getMessage());
        }
        return "cocina/tablero";
    }

    @PostMapping("/pedidos/{id}/avanzar")
    public String avanzar(@PathVariable String id, @RequestParam ItemPedido.Estado estado, RedirectAttributes flash) {
        try {
            pedidos.avanzarTodos(id, estado);
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cocina";
    }

    @PostMapping("/reportar-agotado")
    public String reportar(@RequestParam String insumoId, @RequestParam(required = false) String observacion,
            RedirectAttributes flash) {
        try {
            inventario.reportarAgotado(insumoId, observacion);
            flash.addFlashAttribute("exito", "Reporte enviado al responsable de inventario");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", "No se pudo enviar el reporte: " + ex.getMessage());
        }
        return "redirect:/cocina";
    }
}
