package com.wokcontrol.facturacion.controller.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.facturacion.client.PedidosClient;
import com.wokcontrol.facturacion.dto.FacturacionDtos;
import com.wokcontrol.facturacion.dto.FacturacionDtos.AutorizacionRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CobroRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.DescuentoRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PagoRequest;
import com.wokcontrol.facturacion.entity.AutorizacionDescuento;
import com.wokcontrol.facturacion.entity.CierreCaja;
import com.wokcontrol.facturacion.entity.Factura;
import com.wokcontrol.facturacion.service.CierreCajaService;
import com.wokcontrol.facturacion.service.DescuentoService;
import com.wokcontrol.facturacion.service.FacturacionService;

@Controller
@RequestMapping("/caja")
public class CajaWebController {

    private final FacturacionService facturacion;
    private final CierreCajaService cierres;
    private final DescuentoService descuentos;
    private final PedidosClient pedidos;
    private final ZoneId zona;

    public CajaWebController(FacturacionService facturacion, CierreCajaService cierres, DescuentoService descuentos,
            PedidosClient pedidos, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.facturacion = facturacion;
        this.cierres = cierres;
        this.descuentos = descuentos;
        this.pedidos = pedidos;
        this.zona = ZoneId.of(zona);
    }

    public static class PagoForm {
        private Factura.MetodoPago metodo;
        private BigDecimal monto;
        private String referencia;

        public Factura.MetodoPago getMetodo() { return metodo; }
        public void setMetodo(Factura.MetodoPago metodo) { this.metodo = metodo; }
        public BigDecimal getMonto() { return monto; }
        public void setMonto(BigDecimal monto) { this.monto = monto; }
        public String getReferencia() { return referencia; }
        public void setReferencia(String referencia) { this.referencia = referencia; }
    }

    public static class CobroForm {
        private String claveIdempotencia;
        private List<String> pedidoIds = new ArrayList<>();
        private List<PagoForm> pagos = new ArrayList<>();
        private BigDecimal descuentoPorcentaje;
        private String descuentoMotivo;
        private String codigoAutorizacion;

        public String getClaveIdempotencia() { return claveIdempotencia; }
        public void setClaveIdempotencia(String c) { this.claveIdempotencia = c; }
        public List<String> getPedidoIds() { return pedidoIds; }
        public void setPedidoIds(List<String> pedidoIds) { this.pedidoIds = pedidoIds; }
        public List<PagoForm> getPagos() { return pagos; }
        public void setPagos(List<PagoForm> pagos) { this.pagos = pagos; }
        public BigDecimal getDescuentoPorcentaje() { return descuentoPorcentaje; }
        public void setDescuentoPorcentaje(BigDecimal d) { this.descuentoPorcentaje = d; }
        public String getDescuentoMotivo() { return descuentoMotivo; }
        public void setDescuentoMotivo(String d) { this.descuentoMotivo = d; }
        public String getCodigoAutorizacion() { return codigoAutorizacion; }
        public void setCodigoAutorizacion(String c) { this.codigoAutorizacion = c; }
    }

    @GetMapping
    public String inicio() {
        return "redirect:/caja/cuentas";
    }

    // ------------------------------------------------------------------ cuentas y cobro

    @GetMapping("/cuentas")
    public String cuentas(Model model) {
        try {
            model.addAttribute("mesas", pedidos.mesas().stream().filter(m -> m.pedidosActivos() > 0).toList());
        } catch (WokException ex) {
            model.addAttribute("mesas", List.of());
            model.addAttribute("advertencia", "No fue posible consultar las mesas: " + ex.getMessage());
        }
        model.addAttribute("pendientes", facturacion.pendientesSincronizar());
        return "caja/cuentas";
    }

    @GetMapping("/cuentas/{mesaId}")
    public String cuenta(@PathVariable String mesaId, Model model) {
        CobroForm f = new CobroForm();
        f.setClaveIdempotencia(UUID.randomUUID().toString());
        PagoForm p = new PagoForm();
        p.setMetodo(Factura.MetodoPago.EFECTIVO);
        f.getPagos().add(p);
        f.getPagos().add(new PagoForm());
        return vistaCuenta(mesaId, f, model);
    }

    @PostMapping("/cuentas/{mesaId}/cobrar")
    public String cobrar(@PathVariable String mesaId, @ModelAttribute("form") CobroForm form, Model model,
            RedirectAttributes flash) {
        try {
            List<PagoRequest> pagos = form.getPagos().stream()
                    .filter(p -> p != null && p.getMetodo() != null && p.getMonto() != null && p.getMonto().signum() > 0)
                    .map(p -> new PagoRequest(p.getMetodo(), p.getMonto(), p.getReferencia())).toList();
            if (pagos.isEmpty()) {
                throw new ReglaNegocioException("Registre al menos un pago con monto");
            }
            DescuentoRequest descuento = form.getDescuentoPorcentaje() != null && form.getDescuentoPorcentaje().signum() > 0
                    ? new DescuentoRequest(form.getDescuentoPorcentaje(), form.getDescuentoMotivo(), form.getCodigoAutorizacion())
                    : null;
            if (form.getPedidoIds() == null || form.getPedidoIds().isEmpty()) {
                throw new ReglaNegocioException("Seleccione al menos un pedido para cobrar");
            }
            Factura f = facturacion.cobrar(new CobroRequest(mesaId, form.getPedidoIds(), pagos, descuento,
                    form.getClaveIdempotencia()));
            flash.addFlashAttribute("exito", "Comprobante " + f.getNumero() + " emitido"
                    + (f.getCambio().signum() > 0 ? ". Cambio a entregar: $ " + f.getCambio().toPlainString() : ""));
            return "redirect:/caja/comprobantes/" + f.getId();
        } catch (WokException ex) {
            model.addAttribute("error", ex.getMessage());
            return vistaCuenta(mesaId, form, model);
        }
    }

    private String vistaCuenta(String mesaId, CobroForm form, Model model) {
        var cuenta = facturacion.cuenta(mesaId);
        if (form.getPedidoIds().isEmpty()) {
            cuenta.pedidos().forEach(p -> form.getPedidoIds().add(p.id()));
        }
        while (form.getPagos().size() < 2) {
            form.getPagos().add(new PagoForm());
        }
        model.addAttribute("cuenta", cuenta);
        model.addAttribute("form", form);
        model.addAttribute("metodos", Factura.MetodoPago.values());
        model.addAttribute("puedeDescontarDirecto", UsuarioActual.requerido().tiene(com.wokcontrol.common.security.Permisos.DESCUENTOS_AUTORIZAR));
        model.addAttribute("descuentoMaximo", FacturacionDtos.DESCUENTO_MAXIMO);
        return "caja/cuenta";
    }

    // ------------------------------------------------------------------ comprobantes

    @GetMapping("/comprobantes")
    public String comprobantes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Factura.Estado estado, @RequestParam(required = false) String cajero, Model model) {
        LocalDate d = desde == null ? LocalDate.now(zona) : desde;
        LocalDate h = hasta == null ? d : hasta;
        List<Factura> lista = facturacion.buscar(d, h, estado, cajero, null);
        model.addAttribute("facturas", lista);
        model.addAttribute("desde", d);
        model.addAttribute("hasta", h);
        model.addAttribute("estado", estado);
        model.addAttribute("cajero", cajero);
        model.addAttribute("estados", Factura.Estado.values());
        model.addAttribute("totalEmitido", lista.stream().filter(f -> f.getEstado() == Factura.Estado.EMITIDA)
                .map(Factura::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "caja/comprobantes";
    }

    @GetMapping("/comprobantes/{id}")
    public String comprobante(@PathVariable String id, Model model) {
        model.addAttribute("f", facturacion.obtener(id));
        return "caja/comprobante";
    }

    @PostMapping("/comprobantes/{id}/anular")
    public String anular(@PathVariable String id, @RequestParam(required = false) String motivo, RedirectAttributes flash) {
        try {
            Factura f = facturacion.anular(id, motivo);
            flash.addFlashAttribute("exito", "Comprobante " + f.getNumero() + " anulado; los pedidos vuelven a quedar por cobrar");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/caja/comprobantes/" + id;
    }

    @PostMapping("/comprobantes/{id}/sincronizar")
    public String sincronizar(@PathVariable String id, RedirectAttributes flash) {
        Factura f = facturacion.reintentar(id);
        if (f.getSincronizacion() == Factura.Sincronizacion.COMPLETA) {
            flash.addFlashAttribute("exito", "Pedidos actualizados correctamente");
        } else {
            flash.addFlashAttribute("error", "pedidos-service sigue sin responder: " + f.getErrorSincronizacion());
        }
        return "redirect:/caja/comprobantes/" + id;
    }

    // ------------------------------------------------------------------ cierres

    @GetMapping("/cierres")
    public String cierres(Model model) {
        UsuarioActual u = UsuarioActual.requerido();
        if (u.tiene(com.wokcontrol.common.security.Permisos.CAJA_CERRAR)) {
            model.addAttribute("preliminar", cierres.preliminar(u.responsable()));
        }
        model.addAttribute("cierres", cierres.recientes());
        model.addAttribute("metodos", Factura.MetodoPago.values());
        return "caja/cierres";
    }

    @GetMapping("/cierres/{id}")
    public String cierre(@PathVariable String id, Model model) {
        CierreCaja c = cierres.obtener(id);
        model.addAttribute("c", c);
        model.addAttribute("comprobantes", cierres.comprobantesDe(id));
        model.addAttribute("metodos", Factura.MetodoPago.values());
        return "caja/cierre";
    }

    @PostMapping("/cierres")
    public String cerrar(@RequestParam Map<String, String> parametros, RedirectAttributes flash) {
        try {
            Map<Factura.MetodoPago, BigDecimal> declarado = new EnumMap<>(Factura.MetodoPago.class);
            for (Factura.MetodoPago m : Factura.MetodoPago.values()) {
                String v = parametros.get("declarado_" + m.name());
                declarado.put(m, v == null || v.isBlank() ? BigDecimal.ZERO : new BigDecimal(v.trim()));
            }
            CierreCaja c = cierres.cerrar(declarado, parametros.get("observaciones"));
            flash.addFlashAttribute("exito", "Cierre " + c.getNumero() + " registrado");
            return "redirect:/caja/cierres/" + c.getId();
        } catch (NumberFormatException ex) {
            flash.addFlashAttribute("error", "Los montos declarados deben ser números");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/caja/cierres";
    }

    // ------------------------------------------------------------------ descuentos

    @GetMapping("/descuentos")
    public String descuentos(Model model) {
        model.addAttribute("autorizaciones", descuentos.recientes());
        try {
            model.addAttribute("mesas", pedidos.mesas());
        } catch (WokException ex) {
            model.addAttribute("mesas", List.of());
        }
        return "caja/descuentos";
    }

    @PostMapping("/descuentos")
    public String autorizar(@RequestParam(required = false) BigDecimal porcentajeMaximo, @RequestParam(required = false) String motivo,
            @RequestParam(required = false) String mesaId, @RequestParam(required = false) Integer horasVigencia,
            RedirectAttributes flash) {
        try {
            if (porcentajeMaximo == null || porcentajeMaximo.compareTo(BigDecimal.ONE) < 0
                    || porcentajeMaximo.compareTo(FacturacionDtos.DESCUENTO_MAXIMO) > 0) {
                throw new ReglaNegocioException("El porcentaje debe estar entre 1% y 50%");
            }
            if (motivo == null || motivo.trim().length() < 5) {
                throw new ReglaNegocioException("Indique el motivo (mínimo 5 caracteres)");
            }
            AutorizacionDescuento a = descuentos.autorizar(new AutorizacionRequest(porcentajeMaximo, motivo,
                    mesaId == null || mesaId.isBlank() ? null : mesaId, horasVigencia));
            flash.addFlashAttribute("exito", "Código de autorización: " + a.getCodigo() + " (hasta "
                    + a.getPorcentajeMaximo().stripTrailingZeros().toPlainString() + "%, un solo uso)");
        } catch (WokException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/caja/descuentos";
    }
}
