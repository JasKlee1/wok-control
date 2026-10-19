package com.wokcontrol.pedidos.controller.web;

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
import com.wokcontrol.pedidos.dto.PedidoDtos;
import com.wokcontrol.pedidos.dto.PedidoDtos.MesaRequest;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Controller
@RequestMapping("/mesas")
public class MesaWebController {

    private final MesaService mesas;
    private final PedidoService pedidos;

    public MesaWebController(MesaService mesas, PedidoService pedidos) {
        this.mesas = mesas;
        this.pedidos = pedidos;
    }

    public static class MesaForm {
        private String id;
        @Min(value = 1, message = "El número debe ser positivo")
        @Max(999)
        private int numero = 1;
        @Min(value = 1, message = "Capacidad mínima 1")
        @Max(value = 30, message = "Capacidad máxima 30")
        private int capacidad = 4;
        @NotBlank(message = "Seleccione la zona")
        private String zona = "SALON";
        private boolean activa = true;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public int getNumero() { return numero; }
        public void setNumero(int numero) { this.numero = numero; }
        public int getCapacidad() { return capacidad; }
        public void setCapacidad(int capacidad) { this.capacidad = capacidad; }
        public String getZona() { return zona; }
        public void setZona(String zona) { this.zona = zona; }
        public boolean isActiva() { return activa; }
        public void setActiva(boolean activa) { this.activa = activa; }
        public boolean esNueva() { return id == null || id.isBlank(); }
    }

    @GetMapping
    public String tablero(@RequestParam(required = false) String zona, Model model) {
        var tablero = mesas.tablero(false).stream().filter(m -> zona == null || zona.isBlank() || zona.equals(m.zona())).toList();
        model.addAttribute("mesas", tablero);
        model.addAttribute("zonas", PedidoDtos.ZONAS);
        model.addAttribute("zona", zona);
        model.addAttribute("disponibles", tablero.stream().filter(m -> m.estado() == Mesa.Estado.DISPONIBLE).count());
        model.addAttribute("ocupadas", tablero.stream().filter(m -> m.estado() == Mesa.Estado.OCUPADA).count());
        return "mesas/tablero";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable String id, Model model) {
        model.addAttribute("mesa", mesas.obtener(id));
        model.addAttribute("pedidos", pedidos.activosDeMesa(id));
        return "mesas/detalle";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        return formulario(model, new MesaForm());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        Mesa m = mesas.obtener(id);
        MesaForm f = new MesaForm();
        f.setId(m.getId());
        f.setNumero(m.getNumero());
        f.setCapacidad(m.getCapacidad());
        f.setZona(m.getZona());
        f.setActiva(m.isActiva());
        return formulario(model, f);
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("form") MesaForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (br.hasErrors()) {
            return formulario(model, form);
        }
        try {
            MesaRequest req = new MesaRequest(form.getNumero(), form.getCapacidad(), form.getZona(), form.isActiva());
            if (form.esNueva()) {
                mesas.crear(req);
            } else {
                mesas.actualizar(form.getId(), req);
            }
            flash.addFlashAttribute("exito", "Mesa " + form.getNumero() + " guardada");
            return "redirect:/mesas";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form);
        }
    }

    @PostMapping("/{id}/liberar")
    public String liberar(@PathVariable String id, RedirectAttributes flash) {
        try {
            Mesa m = mesas.liberar(id);
            flash.addFlashAttribute("exito", "Mesa " + m.getNumero() + " disponible");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/mesas";
    }

    @PostMapping("/{id}/servicio")
    public String servicio(@PathVariable String id, @RequestParam boolean fuera, RedirectAttributes flash) {
        try {
            Mesa m = mesas.fueraDeServicio(id, fuera);
            flash.addFlashAttribute("exito", "Mesa " + m.getNumero() + (fuera ? " fuera de servicio" : " habilitada"));
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/mesas";
    }

    private String formulario(Model model, MesaForm form) {
        model.addAttribute("form", form);
        model.addAttribute("zonas", PedidoDtos.ZONAS);
        return "mesas/form";
    }
}
