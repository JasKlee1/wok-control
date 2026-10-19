package com.wokcontrol.reportes.controller.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.reportes.client.ServiciosClient;
import com.wokcontrol.reportes.dto.ReportesDtos.CostoPlato;
import com.wokcontrol.reportes.dto.ReportesDtos.Dashboard;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenVentas;
import com.wokcontrol.reportes.service.CostosService;
import com.wokcontrol.reportes.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Reportes", description = "Indicadores agregados desde los demás servicios con los permisos del usuario")
public class ReportesApiRestController {

    private final DashboardService dashboard;
    private final CostosService costos;
    private final ServiciosClient servicios;

    public ReportesApiRestController(DashboardService dashboard, CostosService costos, ServiciosClient servicios) {
        this.dashboard = dashboard;
        this.costos = costos;
        this.servicios = servicios;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Panel del usuario", description = "Solo incluye las secciones que su rol puede consultar; "
            + "las secciones no disponibles indican el motivo")
    public Dashboard dashboard() {
        return dashboard.construir();
    }

    @GetMapping("/ventas")
    @Operation(summary = "Ventas en un rango de fechas")
    public ResumenVentas ventas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicios.ventas(servicios.credenciales(), desde, hasta);
    }

    @GetMapping("/costos-platos")
    @Operation(summary = "Costo y margen por plato", description = "Receta × costo promedio actual de cada insumo")
    public List<CostoPlato> costosPlatos() {
        return costos.costosPlatos();
    }
}
