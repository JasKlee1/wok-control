package com.wokcontrol.domicilios.controller.web;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.domicilios.client.MenuClient;
import com.wokcontrol.domicilios.client.QrRutaClient;
import com.wokcontrol.domicilios.dto.DomicilioDtos.DomicilioRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.EdicionRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.ItemRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.TipoProducto;
import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.entity.EstadoDomicilio;
import com.wokcontrol.domicilios.service.CampoInvalidoException;
import com.wokcontrol.domicilios.service.DomicilioService;

/** v2 · HU-047/048: pantallas del cajero para los domicilios. */
@Controller
@RequestMapping("/domicilios")
public class DomicilioWebController {

    private final DomicilioService servicio;
    private final MenuClient menu;
    private final QrRutaClient qr;

    public DomicilioWebController(DomicilioService servicio, MenuClient menu, QrRutaClient qr) {
        this.servicio = servicio;
        this.menu = menu;
        this.qr = qr;
    }

    // ------------------------------------------------------------------ formulario

    public static class LineaForm {
        private String clave;
        private Integer cantidad = 0;
        private Integer nivelPicante = 0;
        private String notas;

        public String getClave() { return clave; }
        public void setClave(String clave) { this.clave = clave; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
        public Integer getNivelPicante() { return nivelPicante; }
        public void setNivelPicante(Integer nivelPicante) { this.nivelPicante = nivelPicante; }
        public String getNotas() { return notas; }
        public void setNotas(String notas) { this.notas = notas; }
    }

    public static class DomicilioForm {
        private String id;
        private String cliente;
        private String telefono;
        private String direccion;
        private String referencia;
        private BigDecimal valorDomicilio = BigDecimal.valueOf(5000);
        private String observaciones;
        private List<LineaForm> lineas = new ArrayList<>();

        public boolean esNuevo() { return id == null || id.isBlank(); }

        List<ItemRequest> items() {
            List<ItemRequest> r = new ArrayList<>();
            for (LineaForm l : lineas) {
                if (l.getClave() == null || l.getCantidad() == null || l.getCantidad() <= 0) {
                    continue;
                }
                String[] partes = l.getClave().split(":", 2);
                r.add(new ItemRequest(TipoProducto.valueOf(partes[0]), partes[1], l.getCantidad(),
                        l.getNivelPicante() == null ? 0 : l.getNivelPicante(), l.getNotas()));
            }
            return r;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCliente() { return cliente; }
        public void setCliente(String cliente) { this.cliente = cliente; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public String getDireccion() { return direccion; }
        public void setDireccion(String direccion) { this.direccion = direccion; }
        public String getReferencia() { return referencia; }
        public void setReferencia(String referencia) { this.referencia = referencia; }
        public BigDecimal getValorDomicilio() { return valorDomicilio; }
        public void setValorDomicilio(BigDecimal valorDomicilio) { this.valorDomicilio = valorDomicilio; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
        public List<LineaForm> getLineas() { return lineas; }
        public void setLineas(List<LineaForm> lineas) { this.lineas = lineas; }
    }

    /** Fila de la carta en el formulario, alineada por índice con {@code form.lineas}. */
    public record FilaCarta(int idx, String categoria, boolean primeraDeSeccion, MenuClient.ProductoCarta producto) {
    }

    // ------------------------------------------------------------------ vistas

    @GetMapping
    public String lista(Model model) {
        List<Domicilio> activos = servicio.activos();
        java.util.Map<String, Long> conteo = new java.util.LinkedHashMap<>();
        conteo.put("En cocina", activos.stream().filter(d -> EstadoDomicilio.EN_COCINA.contains(d.getEstado())).count());
        conteo.put("Listos sin asignar", activos.stream().filter(d -> d.getEstado() == EstadoDomicilio.LISTO).count());
        conteo.put("Asignados", activos.stream().filter(d -> d.getEstado() == EstadoDomicilio.ASIGNADO).count());
        conteo.put("En camino", activos.stream().filter(d -> d.getEstado() == EstadoDomicilio.EN_CAMINO).count());
        model.addAttribute("conteo", conteo);
        model.addAttribute("activos", activos);
        model.addAttribute("cerrados", servicio.cerradosRecientes());
        try {
            model.addAttribute("domiciliarios", servicio.domiciliarios());
        } catch (WokException ex) {
            model.addAttribute("domiciliarios", List.of());
            model.addAttribute("advertencia", "No fue posible consultar los domiciliarios: " + ex.getMessage());
        }
        return "domicilios/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new DomicilioForm(), null);
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable String id, Model model) {
        Domicilio d = servicio.obtener(id);
        servicio.sincronizarCocina(List.of(d));
        model.addAttribute("d", d);
        model.addAttribute("urlRuta", servicio.urlRuta(d));
        model.addAttribute("qrProveedor", qr.proveedor());
        model.addAttribute("qrApi", qr.urlApi(d.getCliente().direccion()).toString());
        if (d.getEstado() == EstadoDomicilio.LISTO
                || d.getEstado() == EstadoDomicilio.ASIGNADO) {
            try {
                model.addAttribute("domiciliarios", servicio.domiciliarios());
            } catch (WokException ex) {
                model.addAttribute("domiciliarios", List.of());
            }
        }
        return "domicilios/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model, RedirectAttributes flash) {
        Domicilio d = servicio.obtener(id);
        if (!d.getEstado().antesDelDespacho()) {
            flash.addFlashAttribute("error", "YA_DESPACHADO: el domicilio " + d.getNumero() + " ya salió y no se puede editar");
            return "redirect:/domicilios/" + id;
        }
        DomicilioForm f = new DomicilioForm();
        f.setId(d.getId());
        f.setCliente(d.getCliente().nombre());
        f.setTelefono(d.getCliente().telefono());
        f.setDireccion(d.getCliente().direccion());
        f.setReferencia(d.getCliente().referencia());
        f.setValorDomicilio(d.getValorDomicilio());
        f.setObservaciones(d.getObservaciones());
        return formulario(model, f, d);
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("form") DomicilioForm form, Model model, RedirectAttributes flash) {
        try {
            Domicilio d = form.esNuevo()
                    ? servicio.registrar(new DomicilioRequest(form.getCliente(), form.getTelefono(), form.getDireccion(),
                            form.getReferencia(), form.getValorDomicilio(), form.items(), form.getObservaciones()))
                    : servicio.editar(form.getId(), new EdicionRequest(form.getCliente(), form.getTelefono(), form.getDireccion(),
                            form.getReferencia(), form.getValorDomicilio(), form.getObservaciones()));
            flash.addFlashAttribute("exito", form.esNuevo()
                    ? "Domicilio " + d.getNumero() + " registrado: el pedido " + d.getPedidoNumero() + " ya está en cocina"
                    : "Domicilio " + d.getNumero() + " actualizado");
            return "redirect:/domicilios/" + d.getId();
        } catch (CampoInvalidoException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("campoError", ex.getCampo());
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return formulario(model, form, form.esNuevo() ? null : servicio.obtener(form.getId()));
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes flash) {
        try {
            Domicilio d = servicio.cancelar(id, motivo);
            flash.addFlashAttribute("exito", "Domicilio " + d.getNumero() + " cancelado");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/domicilios/" + id;
    }

    @PostMapping("/{id}/asignar")
    public String asignar(@PathVariable String id, @RequestParam(required = false) String domiciliario,
            @RequestParam(defaultValue = "detalle") String volver, RedirectAttributes flash) {
        try {
            Domicilio d = servicio.asignar(id, domiciliario == null ? "" : domiciliario);
            flash.addFlashAttribute("exito", "Domicilio " + d.getNumero() + " asignado a " + d.getDomiciliarioNombre());
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "lista".equals(volver) ? "redirect:/domicilios" : "redirect:/domicilios/" + id;
    }

    /** Rótulo imprimible para la bolsa del pedido, con el QR de la ruta. */
    @GetMapping("/{id}/rotulo")
    public String rotulo(@PathVariable String id, Model model) {
        Domicilio d = servicio.obtener(id);
        model.addAttribute("d", d);
        model.addAttribute("urlRuta", servicio.urlRuta(d));
        model.addAttribute("qrProveedor", qr.proveedor());
        return "domicilios/rotulo";
    }

    /** API externa (ADR-005): PNG del QR; el cajero ve cualquiera y el domiciliario solo los suyos. */
    @GetMapping(value = "/{id}/qr-ruta.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qrRuta(@PathVariable String id) {
        return servicio.qrRuta(id, UsuarioActual.requerido())
                .map(png -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate()).body(png))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    private String formulario(Model model, DomicilioForm form, Domicilio existente) {
        model.addAttribute("form", form);
        model.addAttribute("existente", existente);
        List<FilaCarta> filas = new ArrayList<>();
        if (existente == null) {
            List<MenuClient.SeccionCarta> carta;
            try {
                carta = menu.carta();
            } catch (WokException ex) {
                carta = List.of();
                model.addAttribute("advertencia", "No fue posible cargar la carta desde menu-service: " + ex.getMessage());
            }
            List<LineaForm> lineas = new ArrayList<>();
            for (MenuClient.SeccionCarta s : carta) {
                boolean primera = true;
                for (MenuClient.ProductoCarta p : s.productos()) {
                    String clave = p.tipo() + ":" + p.id();
                    LineaForm l = form.getLineas().stream().filter(x -> clave.equals(x.getClave())).findFirst().orElse(null);
                    if (l == null) {
                        l = new LineaForm();
                        l.setClave(clave);
                        l.setNivelPicante(p.picanteBase());
                    }
                    filas.add(new FilaCarta(lineas.size(), s.categoria(), primera, p));
                    lineas.add(l);
                    primera = false;
                }
            }
            form.setLineas(lineas);
        }
        model.addAttribute("filas", filas);
        return "domicilios/form";
    }
}
