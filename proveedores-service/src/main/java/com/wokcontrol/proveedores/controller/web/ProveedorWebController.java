package com.wokcontrol.proveedores.controller.web;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import com.wokcontrol.proveedores.client.InventarioClient;
import com.wokcontrol.proveedores.dto.ProveedorDtos;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ProveedorRequest;
import com.wokcontrol.proveedores.entity.Proveedor;
import com.wokcontrol.proveedores.service.OrdenCompraService;
import com.wokcontrol.proveedores.service.ProveedorService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Controller
@RequestMapping("/proveedores")
public class ProveedorWebController {

    private final ProveedorService proveedores;
    private final OrdenCompraService ordenes;
    private final InventarioClient inventario;

    public ProveedorWebController(ProveedorService proveedores, OrdenCompraService ordenes, InventarioClient inventario) {
        this.proveedores = proveedores;
        this.ordenes = ordenes;
        this.inventario = inventario;
    }

    public static class ProveedorForm {
        private String id;
        @NotBlank(message = "El NIT es obligatorio")
        @Pattern(regexp = "^[0-9]{5,15}(-[0-9])?$", message = "NIT inválido (números y dígito de verificación opcional)")
        private String nit;
        @NotBlank(message = "La razón social es obligatoria")
        @Size(max = 120)
        private String razonSocial;
        @NotBlank(message = "El nombre comercial es obligatorio")
        @Size(max = 80)
        private String nombreComercial;
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = ProveedorDtos.TELEFONO, message = "Teléfono inválido")
        private String telefono;
        @Email(message = "Correo inválido")
        private String email;
        @Size(max = 150)
        private String direccion;
        @Size(max = 60)
        private String ciudad;
        @Size(max = 80)
        private String contactoNombre;
        @Size(max = 60)
        private String contactoCargo;
        @Pattern(regexp = "^$|" + ProveedorDtos.TELEFONO, message = "Teléfono inválido")
        private String contactoTelefono;
        @Email(message = "Correo inválido")
        private String contactoEmail;
        private Set<String> categorias = new LinkedHashSet<>();
        private Set<String> insumoIds = new LinkedHashSet<>();
        @Min(value = 0, message = "No puede ser negativo")
        @Max(365)
        private Integer plazoPagoDias;
        @DecimalMin(value = "0", message = "No puede ser negativo")
        private BigDecimal pedidoMinimo;
        @Min(value = 0, message = "No puede ser negativo")
        @Max(90)
        private Integer tiempoEntregaDias;
        @Size(max = 40)
        private String formaPago;
        @Size(max = 250)
        private String observacionesComerciales;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getNit() { return nit; }
        public void setNit(String nit) { this.nit = nit; }
        public String getRazonSocial() { return razonSocial; }
        public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
        public String getNombreComercial() { return nombreComercial; }
        public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDireccion() { return direccion; }
        public void setDireccion(String direccion) { this.direccion = direccion; }
        public String getCiudad() { return ciudad; }
        public void setCiudad(String ciudad) { this.ciudad = ciudad; }
        public String getContactoNombre() { return contactoNombre; }
        public void setContactoNombre(String v) { this.contactoNombre = v; }
        public String getContactoCargo() { return contactoCargo; }
        public void setContactoCargo(String v) { this.contactoCargo = v; }
        public String getContactoTelefono() { return contactoTelefono; }
        public void setContactoTelefono(String v) { this.contactoTelefono = v; }
        public String getContactoEmail() { return contactoEmail; }
        public void setContactoEmail(String v) { this.contactoEmail = v; }
        public Set<String> getCategorias() { return categorias; }
        public void setCategorias(Set<String> categorias) { this.categorias = categorias; }
        public Set<String> getInsumoIds() { return insumoIds; }
        public void setInsumoIds(Set<String> insumoIds) { this.insumoIds = insumoIds; }
        public Integer getPlazoPagoDias() { return plazoPagoDias; }
        public void setPlazoPagoDias(Integer v) { this.plazoPagoDias = v; }
        public BigDecimal getPedidoMinimo() { return pedidoMinimo; }
        public void setPedidoMinimo(BigDecimal v) { this.pedidoMinimo = v; }
        public Integer getTiempoEntregaDias() { return tiempoEntregaDias; }
        public void setTiempoEntregaDias(Integer v) { this.tiempoEntregaDias = v; }
        public String getFormaPago() { return formaPago; }
        public void setFormaPago(String v) { this.formaPago = v; }
        public String getObservacionesComerciales() { return observacionesComerciales; }
        public void setObservacionesComerciales(String v) { this.observacionesComerciales = v; }
        public boolean esNuevo() { return id == null || id.isBlank(); }

        ProveedorRequest aRequest() {
            return new ProveedorRequest(nit, razonSocial, nombreComercial, telefono, vacio(email), direccion, ciudad,
                    contactoNombre, contactoCargo, contactoTelefono == null ? "" : contactoTelefono, vacio(contactoEmail),
                    categorias, insumoIds, plazoPagoDias, pedidoMinimo, tiempoEntregaDias, formaPago, observacionesComerciales);
        }

        private static String vacio(String s) {
            return s == null || s.isBlank() ? null : s;
        }
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String categoria, Model model) {
        model.addAttribute("proveedores", proveedores.buscar(q, activo, categoria, null));
        model.addAttribute("categorias", ProveedorService.CATEGORIAS);
        model.addAttribute("q", q);
        model.addAttribute("activoFiltro", activo);
        model.addAttribute("categoriaFiltro", categoria);
        return "proveedores/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable String id, Model model) {
        model.addAttribute("proveedor", proveedores.obtener(id));
        model.addAttribute("historial", ordenes.historial(id));
        return "proveedores/detalle";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new ProveedorForm());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        Proveedor p = proveedores.obtener(id);
        ProveedorForm f = new ProveedorForm();
        f.setId(p.getId());
        f.setNit(p.getNit());
        f.setRazonSocial(p.getRazonSocial());
        f.setNombreComercial(p.getNombreComercial());
        f.setTelefono(p.getTelefono());
        f.setEmail(p.getEmail());
        f.setDireccion(p.getDireccion());
        f.setCiudad(p.getCiudad());
        if (p.getContacto() != null) {
            f.setContactoNombre(p.getContacto().nombre());
            f.setContactoCargo(p.getContacto().cargo());
            f.setContactoTelefono(p.getContacto().telefono());
            f.setContactoEmail(p.getContacto().email());
        }
        f.setCategorias(new LinkedHashSet<>(p.getCategorias()));
        p.getInsumos().forEach(i -> f.getInsumoIds().add(i.insumoId()));
        if (p.getCondiciones() != null) {
            f.setPlazoPagoDias(p.getCondiciones().plazoPagoDias());
            f.setPedidoMinimo(p.getCondiciones().pedidoMinimo());
            f.setTiempoEntregaDias(p.getCondiciones().tiempoEntregaDias());
            f.setFormaPago(p.getCondiciones().formaPago());
            f.setObservacionesComerciales(p.getCondiciones().observaciones());
        }
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("form") ProveedorForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (br.hasErrors()) {
            return formulario(model, form);
        }
        try {
            Proveedor p = form.esNuevo() ? proveedores.crear(form.aRequest()) : proveedores.actualizar(form.getId(), form.aRequest());
            flash.addFlashAttribute("exito", "Proveedor " + p.getNombreComercial() + " guardado");
            return "redirect:/proveedores/" + p.getId();
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    @PostMapping("/{id}/estado")
    public String estado(@PathVariable String id, @RequestParam boolean activo, RedirectAttributes flash) {
        proveedores.cambiarEstado(id, activo);
        flash.addFlashAttribute("exito", activo ? "Proveedor activado" : "Proveedor desactivado: no podrá recibir órdenes nuevas");
        return "redirect:/proveedores/" + id;
    }

    private String formulario(Model model, ProveedorForm form) {
        model.addAttribute("form", form);
        model.addAttribute("categorias", ProveedorService.CATEGORIAS);
        try {
            model.addAttribute("insumos", inventario.insumos());
        } catch (WokException ex) {
            model.addAttribute("insumos", List.of());
            model.addAttribute("advertencia", "No fue posible consultar inventario-service; no se pueden asociar insumos ahora.");
        }
        return "proveedores/form";
    }
}
