package com.wokcontrol.inventario.controller.web;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.inventario.entity.MovimientoInventario;
import com.wokcontrol.inventario.service.InsumoService;
import com.wokcontrol.inventario.service.MovimientoService;

/** Historial de movimientos y alertas de inventario. */
@Controller
public class InventarioWebController {

    private final MovimientoService movimientos;
    private final InsumoService insumos;

    public InventarioWebController(MovimientoService movimientos, InsumoService insumos) {
        this.movimientos = movimientos;
        this.insumos = insumos;
    }

    @GetMapping("/inventario")
    public String inicio() {
        return "redirect:/inventario/insumos";
    }

    @GetMapping("/inventario/movimientos")
    public String movimientos(@RequestParam(required = false) String insumoId,
            @RequestParam(required = false) MovimientoInventario.Tipo tipo,
            @RequestParam(required = false) String referencia,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", movimientos.buscar(insumoId, tipo, referencia, desde, hasta,
                PageRequest.of(Math.max(page, 0), 30)));
        model.addAttribute("insumos", insumos.buscar(null, null, false, true));
        model.addAttribute("tipos", MovimientoInventario.Tipo.values());
        model.addAttribute("insumoId", insumoId);
        model.addAttribute("tipo", tipo);
        model.addAttribute("referencia", referencia);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("baseUrl", UriComponentsBuilder.fromPath("/inventario/movimientos")
                .queryParamIfPresent("insumoId", Optional.ofNullable(insumoId).filter(s -> !s.isBlank()))
                .queryParamIfPresent("tipo", Optional.ofNullable(tipo))
                .queryParamIfPresent("referencia", Optional.ofNullable(referencia).filter(s -> !s.isBlank()))
                .queryParamIfPresent("desde", Optional.ofNullable(desde))
                .queryParamIfPresent("hasta", Optional.ofNullable(hasta)).build().toUriString());
        return "inventario/movimientos";
    }

    @GetMapping("/inventario/alertas")
    public String alertas(Model model) {
        model.addAttribute("alertas", insumos.alertas());
        model.addAttribute("atendidos", insumos.reportesAtendidosRecientes());
        return "inventario/alertas";
    }

    @PostMapping("/inventario/alertas/{id}/atender")
    public String atender(@PathVariable String id, @RequestParam(required = false) String respuesta,
            RedirectAttributes flash) {
        try {
            insumos.atenderReporte(id, respuesta);
            flash.addFlashAttribute("exito", "Reporte de cocina marcado como atendido");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/inventario/alertas";
    }
}
