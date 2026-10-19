package com.wokcontrol.proveedores.controller.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.proveedores.client.InventarioClient;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ItemOrdenRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.LineaRecepcionRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.OrdenRequest;
import com.wokcontrol.proveedores.entity.OrdenCompra;
import com.wokcontrol.proveedores.service.OrdenCompraService;
import com.wokcontrol.proveedores.service.ProveedorService;

@Controller
@RequestMapping("/compras/ordenes")
public class OrdenCompraWebController {

    private final OrdenCompraService ordenes;
    private final ProveedorService proveedores;
    private final InventarioClient inventario;

    public OrdenCompraWebController(OrdenCompraService ordenes, ProveedorService proveedores, InventarioClient inventario) {
        this.ordenes = ordenes;
        this.proveedores = proveedores;
        this.inventario = inventario;
    }

    public static class ItemForm {
        private String insumoId;
        private BigDecimal cantidad;
        private BigDecimal costoUnitario;

        public String getInsumoId() { return insumoId; }
        public void setInsumoId(String insumoId) { this.insumoId = insumoId; }
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        public BigDecimal getCostoUnitario() { return costoUnitario; }
        public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }
    }

    public static class OrdenForm {
        private String id;
        private String proveedorId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate fechaEntregaEsperada;
        private String notas;
        private List<ItemForm> items = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProveedorId() { return proveedorId; }
        public void setProveedorId(String proveedorId) { this.proveedorId = proveedorId; }
        public LocalDate getFechaEntregaEsperada() { return fechaEntregaEsperada; }
        public void setFechaEntregaEsperada(LocalDate f) { this.fechaEntregaEsperada = f; }
        public String getNotas() { return notas; }
        public void setNotas(String notas) { this.notas = notas; }
        public List<ItemForm> getItems() { return items; }
        public void setItems(List<ItemForm> items) { this.items = items; }
        public boolean esNueva() { return id == null || id.isBlank(); }

        OrdenRequest aRequest() {
            List<ItemOrdenRequest> r = new ArrayList<>();
            for (ItemForm i : items) {
                if (i == null || i.getInsumoId() == null || i.getInsumoId().isBlank()) {
                    continue;
                }
                if (i.getCantidad() == null || i.getCantidad().signum() <= 0) {
                    throw new ReglaNegocioException("Indique una cantidad positiva para cada insumo");
                }
                if (i.getCostoUnitario() == null || i.getCostoUnitario().signum() < 0) {
                    throw new ReglaNegocioException("Indique el costo unitario de cada insumo");
                }
                r.add(new ItemOrdenRequest(i.getInsumoId(), i.getCantidad(), i.getCostoUnitario()));
            }
            if (proveedorId == null || proveedorId.isBlank()) {
                throw new ReglaNegocioException("Seleccione el proveedor");
            }
            if (notas != null && notas.length() > 300) {
                throw new ReglaNegocioException("Las notas admiten máximo 300 caracteres");
            }
            return new OrdenRequest(proveedorId, r, fechaEntregaEsperada, notas);
        }
    }

    public static class RecepcionForm {
        private List<LineaForm> lineas = new ArrayList<>();
        private String observaciones;

        public List<LineaForm> getLineas() { return lineas; }
        public void setLineas(List<LineaForm> lineas) { this.lineas = lineas; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    }

    public static class LineaForm {
        private String insumoId;
        private BigDecimal cantidadRecibida;
        private String lote;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate fechaVencimiento;

        public String getInsumoId() { return insumoId; }
        public void setInsumoId(String insumoId) { this.insumoId = insumoId; }
        public BigDecimal getCantidadRecibida() { return cantidadRecibida; }
        public void setCantidadRecibida(BigDecimal c) { this.cantidadRecibida = c; }
        public String getLote() { return lote; }
        public void setLote(String lote) { this.lote = lote; }
        public LocalDate getFechaVencimiento() { return fechaVencimiento; }
        public void setFechaVencimiento(LocalDate f) { this.fechaVencimiento = f; }
    }

    @GetMapping
    public String lista(@RequestParam(required = false) OrdenCompra.Estado estado,
            @RequestParam(required = false) String proveedorId, Model model) {
        model.addAttribute("ordenes", ordenes.listar(estado, proveedorId));
        model.addAttribute("estados", OrdenCompra.Estado.values());
        model.addAttribute("proveedores", proveedores.buscar(null, null, null, null));
        model.addAttribute("estado", estado);
        model.addAttribute("proveedorId", proveedorId);
        model.addAttribute("resumen", ordenes.resumen());
        return "compras/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable String id, Model model) {
        model.addAttribute("orden", ordenes.obtener(id));
        return "compras/detalle";
    }

    @GetMapping("/nueva")
    public String nueva(@RequestParam(required = false) String proveedorId, Model model) {
        OrdenForm f = new OrdenForm();
        f.setProveedorId(proveedorId);
        f.getItems().add(new ItemForm());
        return formulario(model, f);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model, RedirectAttributes flash) {
        OrdenCompra o = ordenes.obtener(id);
        if (o.getEstado() != OrdenCompra.Estado.BORRADOR) {
            flash.addFlashAttribute("error", "Solo se modifican órdenes en borrador");
            return "redirect:/compras/ordenes/" + id;
        }
        OrdenForm f = new OrdenForm();
        f.setId(o.getId());
        f.setProveedorId(o.getProveedorId());
        f.setFechaEntregaEsperada(o.getFechaEntregaEsperada());
        f.setNotas(o.getNotas());
        for (OrdenCompra.Item i : o.getItems()) {
            ItemForm it = new ItemForm();
            it.setInsumoId(i.insumoId());
            it.setCantidad(i.cantidad());
            it.setCostoUnitario(i.costoUnitario());
            f.getItems().add(it);
        }
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("form") OrdenForm form, Model model, RedirectAttributes flash) {
        try {
            OrdenCompra o = form.esNueva() ? ordenes.crear(form.aRequest()) : ordenes.actualizar(form.getId(), form.aRequest());
            flash.addFlashAttribute("exito", "Orden " + o.getNumero() + " guardada como borrador. Revísela y envíela al proveedor.");
            return "redirect:/compras/ordenes/" + o.getId();
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    @PostMapping("/{id}/enviar")
    public String enviar(@PathVariable String id, RedirectAttributes flash) {
        return accion(id, flash, () -> ordenes.enviar(id), "Orden enviada al proveedor");
    }

    @PostMapping("/{id}/confirmar")
    public String confirmar(@PathVariable String id, RedirectAttributes flash) {
        return accion(id, flash, () -> ordenes.confirmar(id), "Orden confirmada en nombre del proveedor");
    }

    @PostMapping("/{id}/recibir")
    public String recibir(@PathVariable String id, @ModelAttribute RecepcionForm form, RedirectAttributes flash) {
        List<LineaRecepcionRequest> lineas = form.getLineas().stream()
                .filter(l -> l != null && l.getInsumoId() != null)
                .map(l -> new LineaRecepcionRequest(l.getInsumoId(), l.getCantidadRecibida() == null ? BigDecimal.ZERO : l.getCantidadRecibida(),
                        l.getLote(), l.getFechaVencimiento()))
                .toList();
        return accion(id, flash, () -> ordenes.recibir(id, lineas, form.getObservaciones()),
                "Recepción confirmada: la mercancía ingresó al inventario");
    }

    @PostMapping("/{id}/reintentar")
    public String reintentar(@PathVariable String id, RedirectAttributes flash) {
        return accion(id, flash, () -> ordenes.reintentarSincronizacion(id), "Recepción sincronizada con inventario");
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes flash) {
        return accion(id, flash, () -> ordenes.cancelar(id, motivo), "Orden cancelada");
    }

    private String accion(String id, RedirectAttributes flash, Runnable r, String exito) {
        try {
            r.run();
            flash.addFlashAttribute("exito", exito);
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/compras/ordenes/" + id;
    }

    private String formulario(Model model, OrdenForm form) {
        model.addAttribute("form", form);
        model.addAttribute("proveedores", proveedores.buscar(null, true, null, null));
        try {
            model.addAttribute("insumos", inventario.insumos());
        } catch (WokException ex) {
            model.addAttribute("insumos", List.of());
            model.addAttribute("advertencia", "No fue posible consultar inventario-service: " + ex.getMessage());
        }
        return "compras/form";
    }
}
