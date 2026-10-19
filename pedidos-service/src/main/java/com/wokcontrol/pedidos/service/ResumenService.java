package com.wokcontrol.pedidos.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wokcontrol.pedidos.dto.PedidoDtos.MesaResponse;
import com.wokcontrol.pedidos.dto.PedidoDtos.ProductoVendido;
import com.wokcontrol.pedidos.dto.PedidoDtos.ResumenPedidos;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;

/** Indicadores reales del día a partir de los pedidos registrados (sin cifras ficticias). */
@Service
public class ResumenService {

    private final PedidoService pedidos;
    private final MesaService mesas;
    private final ZoneId zona;

    public ResumenService(PedidoService pedidos, MesaService mesas, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.pedidos = pedidos;
        this.mesas = mesas;
        this.zona = ZoneId.of(zona);
    }

    public ResumenPedidos resumen(LocalDate fecha) {
        LocalDate dia = fecha == null ? LocalDate.now(zona) : fecha;
        List<Pedido> delDia = pedidos.buscar(null, null, dia, null);
        long cancelados = delDia.stream().filter(p -> p.getEstado() == EstadoPedido.CANCELADO).count();
        List<Pedido> enCocina = pedidos.comandasCocina();
        long enviados = enCocina.stream().filter(p -> p.getEstado() == EstadoPedido.ENVIADO).count();
        long preparando = enCocina.stream().filter(p -> p.getEstado() == EstadoPedido.EN_PREPARACION).count();
        long listos = enCocina.stream().filter(p -> p.getEstado() == EstadoPedido.LISTO).count();
        List<MesaResponse> tablero = mesas.tablero(false);
        int ocupadas = (int) tablero.stream().filter(m -> m.estado() == Mesa.Estado.OCUPADA).count();
        int disponibles = (int) tablero.stream().filter(m -> m.estado() == Mesa.Estado.DISPONIBLE).count();
        long porCobrar = delDia.stream().filter(p -> p.getEstado() == EstadoPedido.ENTREGADO).count();

        Double promedio = delDia.stream().filter(p -> p.getEnviadoEn() != null && p.getListoEn() != null)
                .mapToLong(p -> Duration.between(p.getEnviadoEn(), p.getListoEn()).toSeconds())
                .average().stream().map(s -> Math.round(s / 6.0) / 10.0).boxed().findFirst().orElse(null);

        Map<String, ProductoVendido> vendidos = new LinkedHashMap<>();
        for (Pedido p : delDia) {
            if (p.getEstado() == EstadoPedido.CANCELADO || p.getEstado() == EstadoPedido.ABIERTO) {
                continue;
            }
            for (ItemPedido it : p.getItems()) {
                vendidos.merge(it.getProductoId(),
                        new ProductoVendido(it.getProductoId(), it.getNombre(), it.getCantidad(), it.getSubtotal()),
                        (a, b) -> new ProductoVendido(a.productoId(), a.nombre(), a.unidades() + b.unidades(),
                                a.ventas().add(b.ventas())));
            }
        }
        List<ProductoVendido> top = vendidos.values().stream()
                .sorted(Comparator.comparingInt(ProductoVendido::unidades).reversed()
                        .thenComparing(ProductoVendido::ventas, Comparator.<BigDecimal>reverseOrder()))
                .limit(5).toList();
        return new ResumenPedidos(delDia.size(), enviados, preparando, listos, porCobrar, cancelados, ocupadas,
                disponibles, tablero.size(), promedio, top);
    }
}
