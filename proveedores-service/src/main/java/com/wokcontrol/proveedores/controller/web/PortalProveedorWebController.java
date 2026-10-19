package com.wokcontrol.proveedores.controller.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.proveedores.dto.ProveedorDtos;
import com.wokcontrol.proveedores.dto.ProveedorDtos.EntregaRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.PerfilProveedorRequest;
import com.wokcontrol.proveedores.entity.Proveedor;
import com.wokcontrol.proveedores.service.OrdenCompraService;
import com.wokcontrol.proveedores.service.ProveedorService;

/**
 * Portal del proveedor externo: su perfil, sus órdenes de compra y el reporte de entregas. Todas las
 * consultas se filtran por el proveedorId firmado en su JWT.
 */
@Controller
@RequestMapping("/portal-proveedor")
public class PortalProveedorWebController {

    private final ProveedorService proveedores;
    private final OrdenCompraService ordenes;

    public PortalProveedorWebController(ProveedorService proveedores, OrdenCompraService ordenes) {
        this.proveedores = proveedores;
        this.ordenes = ordenes;
    }

    @GetMapping
    public String inicio(Model model) {
        Proveedor p = proveedores.miPerfil();
        model.addAttribute("proveedor", p);
        model.addAttribute("ordenes", ordenes.mias());
        model.addAttribute("historial", ordenes.historial(p.getId()));
        return "portal/inicio";
    }

    @GetMapping("/ordenes/{id}")
    public String orden(@PathVariable String id, Model model) {
        model.addAttribute("orden", ordenes.miOrden(id));
        return "portal/orden";
    }

    @PostMapping("/perfil")
    public String perfil(@RequestParam String telefono, @RequestParam(required = false) String email,
            @RequestParam(required = false) String direccion, @RequestParam(required = false) String contactoNombre,
            @RequestParam(required = false) String contactoTelefono, @RequestParam(required = false) String contactoEmail,
            RedirectAttributes flash) {
        try {
            validar(telefono, contactoTelefono, email, contactoEmail);
            proveedores.actualizarMiPerfil(new PerfilProveedorRequest(telefono, email, direccion, contactoNombre,
                    contactoTelefono == null ? "" : contactoTelefono, contactoEmail));
            flash.addFlashAttribute("exito", "Datos de contacto actualizados");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/portal-proveedor";
    }

    @PostMapping("/ordenes/{id}/confirmar")
    public String confirmar(@PathVariable String id, RedirectAttributes flash) {
        try {
            ordenes.confirmar(id);
            flash.addFlashAttribute("exito", "Orden confirmada. Registre la entrega cuando despache la mercancía.");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/portal-proveedor/ordenes/" + id;
    }

    @PostMapping("/ordenes/{id}/entrega")
    public String entrega(@PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEntrega,
            @RequestParam(required = false) String documento, @RequestParam(required = false) String observaciones,
            RedirectAttributes flash) {
        try {
            if (fechaEntrega == null) {
                throw new ReglaNegocioException("Indique la fecha de entrega");
            }
            ordenes.reportarEntrega(id, new EntregaRequest(fechaEntrega, documento, observaciones));
            flash.addFlashAttribute("exito", "Entrega reportada. El restaurante confirmará la recepción de la mercancía.");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/portal-proveedor/ordenes/" + id;
    }

    private static void validar(String telefono, String contactoTelefono, String email, String contactoEmail) {
        if (telefono == null || !telefono.matches(ProveedorDtos.TELEFONO)) {
            throw new ReglaNegocioException("Teléfono inválido");
        }
        if (contactoTelefono != null && !contactoTelefono.isBlank() && !contactoTelefono.matches(ProveedorDtos.TELEFONO)) {
            throw new ReglaNegocioException("Teléfono de contacto inválido");
        }
        String rx = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
        if ((email != null && !email.isBlank() && !email.matches(rx))
                || (contactoEmail != null && !contactoEmail.isBlank() && !contactoEmail.matches(rx))) {
            throw new ReglaNegocioException("Correo inválido");
        }
    }
}
