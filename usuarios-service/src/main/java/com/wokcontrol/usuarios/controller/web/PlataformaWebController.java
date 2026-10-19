package com.wokcontrol.usuarios.controller.web;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.usuarios.dto.ParametrosDtos.ParametrosRequest;
import com.wokcontrol.usuarios.entity.ParametrosGenerales;
import com.wokcontrol.usuarios.service.AuditoriaService;
import com.wokcontrol.usuarios.service.ParametroService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Auditoría y parámetros generales (administrador de plataforma). */
@Controller
public class PlataformaWebController {

    private final AuditoriaService auditoria;
    private final ParametroService parametros;

    public PlataformaWebController(AuditoriaService auditoria, ParametroService parametros) {
        this.auditoria = auditoria;
        this.parametros = parametros;
    }

    public static class ParametrosForm {
        @NotBlank(message = "El nombre del restaurante es obligatorio")
        @Size(max = 100)
        private String nombreRestaurante;
        @Pattern(regexp = "^$|^[0-9.-]{5,20}$", message = "NIT inválido")
        private String nit;
        @Size(max = 150)
        private String direccion;
        @Pattern(regexp = "^$|^[0-9+ ()-]{7,20}$", message = "Teléfono inválido")
        private String telefono;
        @NotNull(message = "Indique el porcentaje")
        @DecimalMin(value = "0", message = "No puede ser negativo")
        @DecimalMax(value = "30", message = "Máximo 30%")
        private java.math.BigDecimal impuestoConsumoPorcentaje;
        @Size(max = 250)
        private String mensajeComprobante;
        @Min(value = 5, message = "Mínimo 5 minutos")
        @Max(value = 120, message = "Máximo 120 minutos")
        private int minutosAlertaCocina = 20;

        public String getNombreRestaurante() { return nombreRestaurante; }
        public void setNombreRestaurante(String v) { this.nombreRestaurante = v; }
        public String getNit() { return nit; }
        public void setNit(String v) { this.nit = v; }
        public String getDireccion() { return direccion; }
        public void setDireccion(String v) { this.direccion = v; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String v) { this.telefono = v; }
        public java.math.BigDecimal getImpuestoConsumoPorcentaje() { return impuestoConsumoPorcentaje; }
        public void setImpuestoConsumoPorcentaje(java.math.BigDecimal v) { this.impuestoConsumoPorcentaje = v; }
        public String getMensajeComprobante() { return mensajeComprobante; }
        public void setMensajeComprobante(String v) { this.mensajeComprobante = v; }
        public int getMinutosAlertaCocina() { return minutosAlertaCocina; }
        public void setMinutosAlertaCocina(int v) { this.minutosAlertaCocina = v; }

        ParametrosRequest aRequest() {
            return new ParametrosRequest(nombreRestaurante, nit, direccion, telefono, impuestoConsumoPorcentaje,
                    mensajeComprobante, minutosAlertaCocina);
        }
    }

    @GetMapping("/plataforma/auditoria")
    public String auditoria(@RequestParam(required = false) String usuario,
            @RequestParam(required = false) String servicio, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", auditoria.buscar(usuario, servicio, q, desde, hasta, PageRequest.of(Math.max(page, 0), 25)));
        model.addAttribute("usuario", usuario);
        model.addAttribute("servicio", servicio);
        model.addAttribute("q", q);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("servicios", new String[] { "usuarios-service", "menu-service", "pedidos-service",
                "inventario-service", "proveedores-service", "facturacion-service" });
        model.addAttribute("baseUrl", UriComponentsBuilder.fromPath("/plataforma/auditoria")
                .queryParamIfPresent("usuario", Optional.ofNullable(usuario).filter(s -> !s.isBlank()))
                .queryParamIfPresent("servicio", Optional.ofNullable(servicio).filter(s -> !s.isBlank()))
                .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                .queryParamIfPresent("desde", Optional.ofNullable(desde))
                .queryParamIfPresent("hasta", Optional.ofNullable(hasta)).build().toUriString());
        return "plataforma/auditoria";
    }

    @GetMapping("/plataforma/parametros")
    public String parametros(Model model) {
        ParametrosGenerales p = parametros.obtener();
        ParametrosForm f = new ParametrosForm();
        f.setNombreRestaurante(p.getNombreRestaurante());
        f.setNit(p.getNit());
        f.setDireccion(p.getDireccion());
        f.setTelefono(p.getTelefono());
        f.setImpuestoConsumoPorcentaje(p.getImpuestoConsumoPorcentaje());
        f.setMensajeComprobante(p.getMensajeComprobante());
        f.setMinutosAlertaCocina(p.getMinutosAlertaCocina());
        model.addAttribute("form", f);
        model.addAttribute("actual", p);
        return "plataforma/parametros";
    }

    @PostMapping("/plataforma/parametros")
    public String guardarParametros(@Valid @ModelAttribute("form") ParametrosForm form, BindingResult br,
            Model model, RedirectAttributes flash) {
        if (br.hasErrors()) {
            model.addAttribute("actual", parametros.obtener());
            return "plataforma/parametros";
        }
        parametros.actualizar(form.aRequest(), UsuarioActual.responsableActual());
        flash.addFlashAttribute("exito", "Parámetros generales actualizados");
        return "redirect:/plataforma/parametros";
    }
}
