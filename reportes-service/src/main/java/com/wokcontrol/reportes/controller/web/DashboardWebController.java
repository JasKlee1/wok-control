package com.wokcontrol.reportes.controller.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.reportes.client.ServiciosClient;
import com.wokcontrol.reportes.dto.ReportesDtos.BarraDia;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenVentas;
import com.wokcontrol.reportes.dto.ReportesDtos.VentaDia;
import com.wokcontrol.reportes.service.CostosService;
import com.wokcontrol.reportes.service.DashboardService;

@Controller
public class DashboardWebController {

    private final DashboardService dashboard;
    private final CostosService costos;
    private final ServiciosClient servicios;
    private final ZoneId zona;

    public DashboardWebController(DashboardService dashboard, CostosService costos, ServiciosClient servicios,
            @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.dashboard = dashboard;
        this.costos = costos;
        this.servicios = servicios;
        this.zona = ZoneId.of(zona);
    }

    @GetMapping("/")
    public String raiz() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String panel(Model model) {
        UsuarioActual u = UsuarioActual.requerido();
        if (u.tiene(Permisos.PORTAL_PROVEEDOR) && u.permisos().size() <= 2) {
            return "redirect:/portal-proveedor";
        }
        model.addAttribute("d", dashboard.construir());
        // los parámetros de fragmentos Thymeleaf no admiten llamadas a beans: la fecha va formateada
        model.addAttribute("fechaHoy", java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",
                java.util.Locale.of("es", "CO")).format(LocalDate.now(zona)));
        return "dashboard";
    }

    @GetMapping("/reportes")
    public String reportes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, Model model) {
        LocalDate h = hasta == null ? LocalDate.now(zona) : hasta;
        LocalDate d = desde == null ? h.minusDays(6) : desde;
        if (d.isAfter(h)) {
            LocalDate t = d;
            d = h;
            h = t;
        }
        if (d.isBefore(h.minusDays(92))) {
            d = h.minusDays(92);
            model.addAttribute("info", "El rango máximo es de 93 días; se ajustó la fecha inicial.");
        }
        model.addAttribute("desde", d);
        model.addAttribute("hasta", h);
        var auth = servicios.credenciales();
        try {
            ResumenVentas v = servicios.ventas(auth, d, h);
            model.addAttribute("ventas", v);
            BigDecimal max = v.porDia().stream().map(VentaDia::total).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal divisor = max.signum() == 0 ? BigDecimal.ONE : max;
            model.addAttribute("barras", v.porDia().stream().map(x -> new BarraDia(x.fecha(), x.comprobantes(), x.total(),
                    x.total().multiply(BigDecimal.valueOf(100)).divide(divisor, 0, java.math.RoundingMode.HALF_UP).intValue()))
                    .toList());
        } catch (WokException ex) {
            model.addAttribute("ventasError", ex.getMessage());
        }
        try {
            model.addAttribute("costos", costos.costosPlatos());
        } catch (WokException ex) {
            model.addAttribute("costos", List.of());
            model.addAttribute("costosError", ex.getMessage());
        }
        try {
            model.addAttribute("valorizacion", servicios.valorizacion(auth));
        } catch (WokException ex) {
            model.addAttribute("inventarioError", ex.getMessage());
        }
        try {
            model.addAttribute("compras", servicios.compras(auth));
        } catch (WokException ex) {
            model.addAttribute("comprasError", ex.getMessage());
        }
        return "reportes";
    }
}
