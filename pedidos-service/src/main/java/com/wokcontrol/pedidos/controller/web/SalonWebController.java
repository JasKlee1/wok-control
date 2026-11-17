package com.wokcontrol.pedidos.controller.web;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.pedidos.client.UsuariosClient;
import com.wokcontrol.pedidos.dto.SalonDtos;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignacionResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignarRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.GrupoRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.GrupoResponse;
import com.wokcontrol.pedidos.entity.GrupoEspera;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.SalonService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * v2 · Épica 8: pantalla del anfitrión, pensada para una tablet. El mapa y la lista de espera se
 * actualizan solos: la página consulta {@code /salon/estado} cada 3 s y, si la huella cambió, pide
 * el fragmento {@code /salon/vivo} (HU-046).
 */
@Controller
@RequestMapping("/salon")
public class SalonWebController {

    /** HU-046 (NFR-WOK-004): cada cuánto consulta la tablet; debe ser menor que los 5 s del criterio. */
    public static final int INTERVALO_MS = 3000;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final SalonService salon;
    private final MesaService mesas;

    public SalonWebController(SalonService salon, MesaService mesas) {
        this.salon = salon;
        this.mesas = mesas;
    }

    public static class GrupoForm {
        @NotBlank(message = SalonDtos.MSG_NOMBRE)
        @Size(max = 60, message = "Máximo 60 caracteres")
        private String nombre;
        @NotNull(message = SalonDtos.MSG_PERSONAS)
        @Min(value = 1, message = SalonDtos.MSG_PERSONAS)
        @Max(value = 50, message = "Máximo 50 personas")
        private Integer personas;
        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Hora en formato HH:mm")
        private String llegada;
        @Size(max = 200, message = "Máximo 200 caracteres")
        private String notas;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public Integer getPersonas() { return personas; }
        public void setPersonas(Integer personas) { this.personas = personas; }
        public String getLlegada() { return llegada; }
        public void setLlegada(String llegada) { this.llegada = llegada; }
        public String getNotas() { return notas; }
        public void setNotas(String notas) { this.notas = notas; }

        GrupoRequest request() {
            return new GrupoRequest(nombre, personas, llegada, notas);
        }
    }

    public static class SentarForm {
        private String grupoId;
        private String nombre;
        private Integer personas;
        private List<String> mesaIds = new ArrayList<>();
        private String mesero;

        public String getGrupoId() { return grupoId; }
        public void setGrupoId(String grupoId) { this.grupoId = grupoId; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public Integer getPersonas() { return personas; }
        public void setPersonas(Integer personas) { this.personas = personas; }
        public List<String> getMesaIds() { return mesaIds; }
        public void setMesaIds(List<String> mesaIds) { this.mesaIds = mesaIds; }
        public String getMesero() { return mesero; }
        public void setMesero(String mesero) { this.mesero = mesero; }
    }

    // ------------------------------------------------------------------ mapa y lista (HU-040, 041, 046)

    @GetMapping
    public String mapa(Model model) {
        GrupoForm f = new GrupoForm();
        f.setLlegada(LocalTime.now(salon.zona()).format(HHMM));
        return pantalla(model, f);
    }

    /** HU-046: fragmento del mapa y la lista de espera que la tablet pide cuando cambia la huella. */
    @GetMapping("/vivo")
    public String vivo(Model model) {
        datosVivos(model);
        return "salon/vivo :: vivo";
    }

    /** HU-046: huella del salón; la tablet la consulta cada {@value #INTERVALO_MS} ms. */
    @GetMapping("/estado")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> estado() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("huella", salon.huella(), "intervaloMs", INTERVALO_MS));
    }

    @PostMapping("/espera")
    public String registrar(@Valid @ModelAttribute("form") GrupoForm form, BindingResult br, Model model,
            RedirectAttributes flash) {
        if (br.hasErrors()) {
            return pantalla(model, form);
        }
        try {
            GrupoEspera g = salon.registrar(form.request());
            flash.addFlashAttribute("exito", g.getNombre() + " quedó en la lista de espera (" + g.getPersonas() + " personas)");
            return "redirect:/salon";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return pantalla(model, form);
        }
    }

    @GetMapping("/espera/{id}/editar")
    public String editar(@PathVariable String id, Model model) {
        GrupoEspera g = salon.grupo(id);
        GrupoForm f = new GrupoForm();
        f.setNombre(g.getNombre());
        f.setPersonas(g.getPersonas());
        f.setLlegada(g.getLlegadaEn() == null ? null : g.getLlegadaEn().atZone(salon.zona()).format(HHMM));
        f.setNotas(g.getNotas());
        model.addAttribute("grupo", g);
        model.addAttribute("form", f);
        return "salon/espera-form";
    }

    @PostMapping("/espera/{id}")
    public String guardarEdicion(@PathVariable String id, @Valid @ModelAttribute("form") GrupoForm form, BindingResult br,
            Model model, RedirectAttributes flash) {
        if (br.hasErrors()) {
            model.addAttribute("grupo", salon.grupo(id));
            return "salon/espera-form";
        }
        try {
            GrupoEspera g = salon.editar(id, form.request());
            flash.addFlashAttribute("exito", "Grupo " + g.getNombre() + " actualizado");
            return "redirect:/salon";
        } catch (WokException ex) {
            model.addAttribute("grupo", salon.grupo(id));
            model.addAttribute("error", ex.getMessage());
            return "salon/espera-form";
        }
    }

    @PostMapping("/espera/{id}/retirar")
    public String retirar(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes flash) {
        try {
            GrupoEspera g = salon.retirar(id, motivo);
            flash.addFlashAttribute("exito", g.getNombre() + " salió de la lista de espera");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/salon";
    }

    // ------------------------------------------------------------------ sentar (HU-042, 045)

    @GetMapping("/sentar")
    public String sentar(@RequestParam(required = false) String grupoId, @RequestParam(required = false) String mesaId,
            Model model) {
        SentarForm f = new SentarForm();
        f.setGrupoId(grupoId);
        if (mesaId != null && !mesaId.isBlank()) {
            f.getMesaIds().add(mesaId);
        }
        return formularioSentar(model, f);
    }

    @PostMapping("/sentar")
    public String sentar(@ModelAttribute("form") SentarForm form, Model model, RedirectAttributes flash) {
        try {
            AsignacionResponse r = salon.asignar(new AsignarRequest(form.getGrupoId(), form.getNombre(), form.getPersonas(),
                    form.getMesaIds(), form.getMesero()));
            String nums = r.grupo().mesaNumeros().stream().map(String::valueOf).reduce((a, b) -> a + " + " + b).orElse("");
            flash.addFlashAttribute("exito", r.grupo().nombre() + " se sentó en " + (r.union() ? "las mesas " : "la mesa ")
                    + nums + " con " + r.grupo().meseroNombre());
            return "redirect:/salon";
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formularioSentar(model, form);
        }
    }

    // ------------------------------------------------------------------ por limpiar (HU-044)

    @PostMapping("/mesas/{id}/limpia")
    public String limpia(@PathVariable String id, RedirectAttributes flash) {
        try {
            Mesa m = mesas.marcarLimpia(id);
            flash.addFlashAttribute("exito", "Mesa " + m.getNumero() + " limpia y libre");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/salon";
    }

    @PostMapping("/mesas/{id}/liberar")
    public String liberar(@PathVariable String id, RedirectAttributes flash) {
        try {
            Mesa m = mesas.liberar(id);
            flash.addFlashAttribute("exito", "Mesa " + m.getNumero() + " " + m.getEstado().etiqueta().toLowerCase());
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/salon";
    }

    // ------------------------------------------------------------------ auxiliares

    private String pantalla(Model model, GrupoForm form) {
        datosVivos(model);
        model.addAttribute("form", form);
        model.addAttribute("intervaloMs", INTERVALO_MS);
        return "salon/mapa";
    }

    private void datosVivos(Model model) {
        model.addAttribute("mapa", salon.mapa());
        model.addAttribute("espera", salon.esperaRespuesta());
        model.addAttribute("ahora", Instant.now());
        model.addAttribute("zona", salon.zona());
    }

    private String formularioSentar(Model model, SentarForm form) {
        GrupoResponse grupo = null;
        if (form.getGrupoId() != null && !form.getGrupoId().isBlank()) {
            grupo = GrupoResponse.de(salon.grupo(form.getGrupoId()), Instant.now());
        }
        List<UsuariosClient.Mesero> meseros = List.of();
        try {
            meseros = salon.meseros();
        } catch (WokException ex) {
            model.addAttribute("errorMeseros", "No se pudo consultar la lista de meseros: " + ex.getMessage());
        }
        var libres = salon.mapa().mesas().stream().filter(m -> m.estado() == Mesa.Estado.DISPONIBLE).toList();
        model.addAttribute("form", form);
        model.addAttribute("grupo", grupo);
        model.addAttribute("libres", libres);
        model.addAttribute("meseros", meseros);
        model.addAttribute("espera", salon.esperaRespuesta());
        return "salon/sentar";
    }
}
