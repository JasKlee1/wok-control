package com.wokcontrol.facturacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wokcontrol.facturacion.dto.FacturacionDtos.ProductoVendido;
import com.wokcontrol.facturacion.dto.FacturacionDtos.ResumenVentas;
import com.wokcontrol.facturacion.dto.FacturacionDtos.VentaDia;
import com.wokcontrol.facturacion.entity.Factura;

/** Indicadores de ventas calculados con los comprobantes registrados (los anulados no suman). */
@Service
public class ReporteVentasService {

    private final FacturacionService facturas;
    private final ZoneId zona;

    public ReporteVentasService(FacturacionService facturas, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.facturas = facturas;
        this.zona = ZoneId.of(zona);
    }

    public ResumenVentas resumen(LocalDate desde, LocalDate hasta) {
        LocalDate d = desde == null ? LocalDate.now(zona) : desde;
        LocalDate h = hasta == null ? d : hasta;
        if (h.isBefore(d)) {
            LocalDate t = d;
            d = h;
            h = t;
        }
        List<Factura> todas = facturas.buscar(d, h, null, null, null);
        List<Factura> emitidas = todas.stream().filter(f -> f.getEstado() == Factura.Estado.EMITIDA).toList();
        BigDecimal ventas = suma(emitidas.stream().map(Factura::getTotal).toList());
        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        for (Factura.MetodoPago m : Factura.MetodoPago.values()) {
            porMetodo.put(m.name(), suma(emitidas.stream().map(f -> f.netoPorMetodo(m)).toList()));
        }
        Map<LocalDate, VentaDia> porDia = new TreeMap<>();
        for (LocalDate x = d; !x.isAfter(h); x = x.plusDays(1)) {
            porDia.put(x, new VentaDia(x, 0, BigDecimal.ZERO));
        }
        Map<String, ProductoVendido> productos = new LinkedHashMap<>();
        for (Factura f : emitidas) {
            LocalDate dia = f.getEmitidaEn().atZone(zona).toLocalDate();
            porDia.computeIfPresent(dia, (k, v) -> new VentaDia(k, v.comprobantes() + 1, v.total().add(f.getTotal())));
            for (Factura.Linea l : f.getLineas()) {
                productos.merge(l.descripcion(), new ProductoVendido(l.descripcion(), l.cantidad(), l.subtotal()),
                        (a, b) -> new ProductoVendido(a.descripcion(), a.unidades() + b.unidades(), a.ventas().add(b.ventas())));
            }
        }
        List<ProductoVendido> top = new ArrayList<>(productos.values());
        top.sort(Comparator.comparingInt(ProductoVendido::unidades).reversed());
        BigDecimal ticket = emitidas.isEmpty() ? BigDecimal.ZERO
                : ventas.divide(BigDecimal.valueOf(emitidas.size()), 0, RoundingMode.HALF_UP);
        return new ResumenVentas(d, h, emitidas.size(), todas.size() - emitidas.size(), ventas,
                suma(emitidas.stream().map(Factura::getSubtotal).toList()),
                suma(emitidas.stream().map(f -> f.getDescuento() == null ? BigDecimal.ZERO : f.getDescuento().valor()).toList()),
                suma(emitidas.stream().map(Factura::getImpuestoValor).toList()), ticket, porMetodo,
                new ArrayList<>(porDia.values()), top.stream().limit(10).toList(), facturas.pendientesSincronizar().size());
    }

    private static BigDecimal suma(List<BigDecimal> l) {
        return l.stream().filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
