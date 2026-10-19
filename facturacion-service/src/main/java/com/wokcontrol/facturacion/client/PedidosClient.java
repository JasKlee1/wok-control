package com.wokcontrol.facturacion.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/** Consulta de cuentas y registro del pago en pedidos-service (idempotente por comprobante). */
@Component
public class PedidosClient {

    private static final String SERVICIO = "pedidos-service";
    private final ClienteInterno cliente;

    public PedidosClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record Item(String productoId, String nombre, BigDecimal precioUnitario, int cantidad, BigDecimal subtotal) {
    }

    public record Pedido(String id, String numero, String mesaId, int mesaNumero, String estado, List<Item> items,
            BigDecimal total, Instant entregadoEn) {
    }

    public record Mesa(String id, int numero, String estado, int pedidosActivos, BigDecimal consumoActual) {
    }

    public List<Pedido> facturables(String mesaId) {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri(u -> u.path("/api/pedidos/facturables").queryParam("mesaId", mesaId).build())
                .headers(cliente.comoServicio()).retrieve()
                .body(new ParameterizedTypeReference<List<Pedido>>() {
                }));
    }

    public Mesa mesa(String mesaId) {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get().uri("/api/mesas/{id}", mesaId)
                .headers(cliente.comoServicio()).retrieve().body(Mesa.class));
    }

    public List<Mesa> mesas() {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get().uri("/api/mesas")
                .headers(cliente.comoServicio()).retrieve()
                .body(new ParameterizedTypeReference<List<Mesa>>() {
                }));
    }

    public void marcarPagado(String pedidoId, String facturaId) {
        cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri("/api/pedidos/{id}/pago", pedidoId)
                .headers(cliente.comoServicio()).body(java.util.Map.of("facturaId", facturaId))
                .retrieve().toBodilessEntity());
    }

    public void revertirPago(String pedidoId, String facturaId) {
        cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri("/api/pedidos/{id}/reversion-pago", pedidoId)
                .headers(cliente.comoServicio()).body(java.util.Map.of("facturaId", facturaId))
                .retrieve().toBodilessEntity());
    }
}
