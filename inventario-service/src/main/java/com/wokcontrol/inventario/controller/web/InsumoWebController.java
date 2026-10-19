package com.wokcontrol.inventario.controller.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.inventario.client.ProveedoresClient;
import com.wokcontrol.inventario.dto.InventarioDtos;
import com.wokcontrol.inventario.dto.InventarioDtos.AjusteRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.InsumoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.MovimientoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.TipoMovimientoManual;
import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.MovimientoInventario;
import com.wokcontrol.inventario.service.InsumoService;
import com.wokcontrol.inventario.service.MovimientoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/inventario/insumos")
public class InsumoWebController {

    private final InsumoService insumos;
    private final MovimientoService movimientos;
    private final ProveedoresClient proveedores;

    public InsumoWebController(InsumoService insumos, MovimientoService movimientos, ProveedoresClient proveedores) {
        this.insumos = insumos;
        this.movimientos = movimientos;
        this.proveedores = proveedores;
    }

    public static class InsumoForm {
        private String id;
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80)
        private String nombre;
        @NotBlank(message = "Seleccione la categoría")
        private String categoria;
        @NotBlank(message = "Seleccione la unidad")
        private String unidad;
        @NotNull(message = "Indique el stock mínimo")
        @DecimalMin(value = "0", message = "No puede ser negativo")
        @DecimalMax(value = "1000000")
        private BigDecimal stockMinimo = BigDecimal.ZERO;
        private boolean perecedero;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getCategoria() { return categoria; }
        public void setCategoria(String categoria) { this.categoria = categoria; }
        public String getUnidad() { return unidad; }
        public void setUnidad(String unidad) { this.unidad = unidad; }
        public BigDecimal getStockMinimo() { return stockMinimo; }
        public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
        public boolean isPerecedero() { return perecedero; }
        public void setPerecedero(boolean perecedero) { this.perecedero = perecedero; }
        public boolean esNuevo() { return id == null || id.isBlank(); }
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String q, @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "false") boolean bajoStock, Model model) {
        model.addAttribute("insumos", insumos.buscar(q, categoria, bajoStock, false));
        model.addAttribute("categorias", InventarioDtos.CATEGORIAS);
        model.addAttribute("valorizacion", insumos.valorizacion());
        model.addAttribute("q", q);
        model.addAttribute("categoriaFiltro", categoria);
        model.addAttribute("bajoStock", bajoStock);
        return "inventario/insumos/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable String id, Model model) {
        Insumo i = insumos.obtener(id);
        model.addAttribute("insumo", i);
        model.addAttribute("movimientos", movimientos.recientes(id));
        model.addAttribute("proveedores", proveedores.deInsumo(id));
        model.addAttribute("tiposManuales", TipoMovimientoManual.values());
        return "inventario/insumos/detalle";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new InsumoForm());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        Insumo i = insumos.obtener(id);
        InsumoForm f = new InsumoForm();
        f.setId(i.getId());
        f.setNombre(i.getNombre());
        f.setCategoria(i.getCategoria());
        f.setUnidad(i.getUnidad());
        f.setStockMinimo(i.getStockMinimo());
        f.setPerecedero(i.isPerecedero());
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("form") InsumoForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (br.hasErrors()) {
            return formulario(model, form);
        }
        try {
            InsumoRequest req = new InsumoRequest(form.getNombre(), form.getCategoria(), form.getUnidad(),
                    form.getStockMinimo(), form.isPerecedero());
            Insumo i = form.esNuevo() ? insumos.crear(req) : insumos.actualizar(form.getId(), req);
            flash.addFlashAttribute("exito", form.esNuevo()
                    ? "Insumo creado con existencia 0. Registre la cantidad inicial con una entrada."
                    : "Insumo actualizado");
            return "redirect:/inventario/insumos/" + i.getId();
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    @PostMapping("/{id}/movimientos")
    public String movimiento(@PathVariable String id, @RequestParam TipoMovimientoManual tipo,
            @RequestParam(required = false) BigDecimal cantidad, @RequestParam(required = false) String motivo,
            @RequestParam(required = false) BigDecimal costoUnitario, @RequestParam(required = false) String lote,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVencimiento,
            RedirectAttributes flash) {
        try {
            if (cantidad == null || cantidad.signum() <= 0) {
                throw new com.wokcontrol.common.exception.ReglaNegocioException("La cantidad debe ser positiva");
            }
            if (costoUnitario != null && costoUnitario.signum() < 0) {
                throw new com.wokcontrol.common.exception.ReglaNegocioException("El costo no puede ser negativo");
            }
            MovimientoInventario m = movimientos.registrar(id,
                    new MovimientoRequest(tipo, cantidad, motivo, costoUnitario, lote, fechaVencimiento));
            flash.addFlashAttribute("exito", "Movimiento registrado: " + m.getTipo() + " de "
                    + m.getCantidad().stripTrailingZeros().toPlainString() + " " + m.getUnidad() + ". Nuevo saldo: "
                    + m.getSaldoNuevo().stripTrailingZeros().toPlainString());
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/inventario/insumos/" + id;
    }

    @PostMapping("/{id}/ajuste")
    public String ajuste(@PathVariable String id, @RequestParam(required = false) BigDecimal cantidadContada,
            @RequestParam(required = false) String motivo, RedirectAttributes flash) {
        try {
            if (motivo == null || motivo.trim().length() < 5) {
                throw new com.wokcontrol.common.exception.ReglaNegocioException("El motivo del ajuste es obligatorio (mínimo 5 caracteres)");
            }
            MovimientoInventario m = movimientos.ajustar(id, new AjusteRequest(cantidadContada, motivo));
            flash.addFlashAttribute("exito", "Ajuste auditado registrado (" + m.getTipo() + "). Nuevo saldo: "
                    + m.getSaldoNuevo().stripTrailingZeros().toPlainString());
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/inventario/insumos/" + id;
    }

    @PostMapping("/{id}/lotes/{codigo}/cerrar")
    public String cerrarLote(@PathVariable String id, @PathVariable String codigo, RedirectAttributes flash) {
        try {
            insumos.cerrarLote(id, codigo);
            flash.addFlashAttribute("exito", "Lote " + codigo + " cerrado: ya no generará alertas de vencimiento");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/inventario/insumos/" + id;
    }

    @PostMapping("/{id}/estado")
    public String estado(@PathVariable String id, @RequestParam boolean activo, RedirectAttributes flash) {
        insumos.cambiarEstado(id, activo);
        flash.addFlashAttribute("exito", activo ? "Insumo reactivado" : "Insumo desactivado: no admite movimientos");
        return "redirect:/inventario/insumos/" + id;
    }

    private String formulario(Model model, InsumoForm form) {
        model.addAttribute("form", form);
        model.addAttribute("categorias", InventarioDtos.CATEGORIAS);
        model.addAttribute("unidades", InventarioDtos.UNIDADES);
        return "inventario/insumos/form";
    }
}
