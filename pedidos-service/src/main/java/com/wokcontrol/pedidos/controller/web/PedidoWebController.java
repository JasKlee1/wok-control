package com.wokcontrol.pedidos.controller.web;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
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

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.SeccionCarta;
import com.wokcontrol.pedidos.dto.PedidoDtos;
import com.wokcontrol.pedidos.dto.PedidoDtos.ActualizarPedidoRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.ItemRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoRequest;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;

@Controller
@RequestMapping("/pedidos")
public class PedidoWebController {

    private final PedidoService pedidos;
    private final MesaService mesas;
    private final MenuClient menu;
    private final ZoneId zona;

    public PedidoWebController(PedidoService pedidos, MesaService mesas, MenuClient menu,
            @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.pedidos = pedidos;
        this.mesas = mesas;
        this.menu = menu;
        this.zona = ZoneId.of(zona);
    }

    /** Una línea por producto de la carta; solo se envían las que tienen cantidad mayor que cero. */
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

    public static class PedidoForm {
        private String id;
        private String mesaId;
        private String observaciones;
        private Set<String> alergias = new LinkedHashSet<>();
        private List<LineaForm> lineas = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getMesaId() { return mesaId; }
        public void setMesaId(String mesaId) { this.mesaId = mesaId; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
        public Set<String> getAlergias() { return alergias; }
        public void setAlergias(Set<String> alergias) { this.alergias = alergias; }
        public List<LineaForm> getLineas() { return lineas; }
        public void setLineas(List<LineaForm> lineas) { this.lineas = lineas; }
        public boolean esNuevo() { return id == null || id.isBlank(); }

        public LineaForm linea(String clave) {
            return lineas.stream().filter(l -> clave.equals(l.getClave())).findFirst().orElse(null);
        }

        List<ItemRequest> items() {
            List<ItemRequest> r = new ArrayList<>();
            for (LineaForm l : lineas) {
                if (l == null || l.getClave() == null || l.getCantidad() == null || l.getCantidad() <= 0) {
                    continue;
                }
                String[] partes = l.getClave().split(":", 2);
                int picante = l.getNivelPicante() == null ? 0 : Math.max(0, Math.min(3, l.getNivelPicante()));
                String notas = l.getNotas() == null ? null : l.getNotas().substring(0, Math.min(200, l.getNotas().length()));
                r.add(new ItemRequest(ItemPedido.Tipo.valueOf(partes[0]), partes[1], Math.min(l.getCantidad(), 50), picante, notas));
            }
            return r;
        }
    }

    @GetMapping
    public String lista(@RequestParam(required = false) EstadoPedido estado, @RequestParam(required = false) String mesaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(defaultValue = "false") boolean mios, Model model) {
        LocalDate dia = fecha == null ? LocalDate.now(zona) : fecha;
        String mesero = mios ? com.wokcontrol.common.security.UsuarioActual.requerido().username() : null;
        model.addAttribute("pedidos", pedidos.buscar(estado, mesaId, dia, mesero));
        model.addAttribute("mesas", mesas.listar(false));
        model.addAttribute("estados", EstadoPedido.values());
        model.addAttribute("estado", estado);
        model.addAttribute("mesaId", mesaId);
        model.addAttribute("fecha", dia);
        model.addAttribute("mios", mios);
        return "pedidos/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable String id, Model model) {
        model.addAttribute("pedido", pedidos.obtener(id));
        return "pedidos/detalle";
    }

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam String mesaId, Model model, RedirectAttributes flash) {
        Mesa mesa = mesas.obtener(mesaId);
        if (!mesa.isActiva() || mesa.getEstado() == Mesa.Estado.FUERA_DE_SERVICIO) {
            flash.addFlashAttribute("error", "La mesa " + mesa.getNumero() + " no está disponible");
            return "redirect:/mesas";
        }
        PedidoForm f = new PedidoForm();
        f.setMesaId(mesaId);
        return formulario(model, f, mesa);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model, RedirectAttributes flash) {
        Pedido p = pedidos.obtener(id);
        if (!p.getEstado().permiteEditarItems()) {
            flash.addFlashAttribute("error", "El pedido ya fue enviado a cocina y no se puede modificar");
            return "redirect:/pedidos/" + id;
        }
        PedidoForm f = new PedidoForm();
        f.setId(p.getId());
        f.setMesaId(p.getMesaId());
        f.setObservaciones(p.getObservaciones());
        f.setAlergias(new LinkedHashSet<>(p.getAlergias()));
        for (ItemPedido it : p.getItems()) {
            String clave = it.getTipo().name() + ":" + it.getProductoId();
            LineaForm l = f.linea(clave);
            if (l == null) {
                l = new LineaForm();
                l.setClave(clave);
                l.setCantidad(0);
                l.setNivelPicante(it.getNivelPicante());
                l.setNotas(it.getNotas());
                f.getLineas().add(l);
            }
            l.setCantidad(l.getCantidad() + it.getCantidad());
        }
        return formulario(model, f, mesas.obtener(p.getMesaId()));
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("form") PedidoForm form, Model model, RedirectAttributes flash) {
        List<ItemRequest> items = form.items();
        try {
            if (items.isEmpty()) {
                throw new com.wokcontrol.common.exception.ReglaNegocioException("Agregue al menos un producto con cantidad mayor que cero");
            }
            Pedido p = form.esNuevo()
                    ? pedidos.crear(new PedidoRequest(form.getMesaId(), items, form.getObservaciones(), form.getAlergias()))
                    : pedidos.actualizar(form.getId(), new ActualizarPedidoRequest(items, form.getObservaciones(), form.getAlergias()));
            flash.addFlashAttribute("exito", "Pedido " + p.getNumero() + " guardado. Revíselo y envíelo a cocina.");
            return "redirect:/pedidos/" + p.getId();
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return formulario(model, form, mesas.obtener(form.getMesaId()));
        }
    }

    @PostMapping("/{id}/enviar")
    public String enviar(@PathVariable String id, RedirectAttributes flash) {
        try {
            Pedido p = pedidos.enviarACocina(id);
            flash.addFlashAttribute("exito", "Pedido " + p.getNumero() + " enviado a cocina");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", "No se envió a cocina: " + ex.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes flash) {
        try {
            Pedido p = pedidos.cancelar(id, motivo);
            flash.addFlashAttribute("exito", "Pedido " + p.getNumero() + " cancelado"
                    + (p.getConsumoInventario() == Pedido.EstadoConsumo.REVERTIDO ? "; el inventario fue restituido" : ""));
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/items/{itemId}/estado")
    public String estadoItem(@PathVariable String id, @PathVariable String itemId, @RequestParam ItemPedido.Estado estado,
            @RequestParam(defaultValue = "detalle") String volver, RedirectAttributes flash) {
        try {
            pedidos.cambiarEstadoItem(id, itemId, estado);
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "cocina".equals(volver) ? "redirect:/cocina" : "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/items/entregar-todos")
    public String entregarTodos(@PathVariable String id, RedirectAttributes flash) {
        try {
            pedidos.avanzarTodos(id, ItemPedido.Estado.ENTREGADO);
            flash.addFlashAttribute("exito", "Productos listos marcados como entregados");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    private String formulario(Model model, PedidoForm form, Mesa mesa) {
        model.addAttribute("form", form);
        model.addAttribute("mesa", mesa);
        model.addAttribute("alergias", PedidoDtos.ALERGIAS);
        List<SeccionCarta> carta;
        try {
            carta = menu.carta();
        } catch (WokException ex) {
            carta = List.of();
            model.addAttribute("advertencia", "No fue posible cargar la carta desde menu-service: " + ex.getMessage());
        }
        // asegura una línea de formulario por cada producto de la carta
        List<LineaForm> lineas = new ArrayList<>();
        for (SeccionCarta s : carta) {
            for (MenuClient.ProductoCarta pr : s.productos()) {
                String clave = pr.tipo() + ":" + pr.id();
                LineaForm l = form.linea(clave);
                if (l == null) {
                    l = new LineaForm();
                    l.setClave(clave);
                    l.setNivelPicante(pr.picanteBase());
                }
                lineas.add(l);
            }
        }
        long fueraDeCarta = form.getLineas().stream().filter(l -> l.getCantidad() != null && l.getCantidad() > 0)
                .filter(l -> lineas.stream().noneMatch(x -> x.getClave().equals(l.getClave()))).count();
        if (fueraDeCarta > 0) {
            model.addAttribute("advertencia", fueraDeCarta + " producto(s) del pedido ya no están disponibles en la carta y se retirarán al guardar.");
        }
        form.setLineas(lineas);
        model.addAttribute("carta", carta);
        return "pedidos/form";
    }
}
