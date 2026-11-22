package com.wokcontrol.domicilios.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/**
 * v2 · CR-001: el pedido de cocina de cada domicilio vive en pedidos-service. Todas las llamadas usan
 * el token de servicio (actor = cajero o domiciliario) y endpoints internos que el gateway bloquea.
 */
@Component
public class PedidosClient {

    private static final String SERVICIO = "pedidos-service";
    private static final String BASE = "/api/pedidos/internos/domicilios";
    private final ClienteInterno cliente;

    public PedidosClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record ItemSolicitado(String tipo, String productoId, int cantidad, int nivelPicante, String notas) {
    }

    public record PedidoDomicilioRequest(String domicilioId, String domicilioNumero, List<ItemSolicitado> items,
            String observaciones) {
    }

    public record ItemCocina(String productoId, String nombre, BigDecimal precioUnitario, int cantidad, BigDecimal subtotal) {
    }

    public record PedidoCocina(String id, String numero, String estado, List<ItemCocina> items, BigDecimal total) {
    }

    /** HU-047: registra el pedido y lo envía a cocina. Idempotente por domicilio en pedidos-service. */
    public PedidoCocina crear(PedidoDomicilioRequest req) {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri(BASE)
                .headers(cliente.comoServicio()).body(req).retrieve().body(PedidoCocina.class));
    }

    /** Estado en cocina de varios pedidos: id → ENVIADO, EN_PREPARACION, LISTO, CANCELADO… */
    public Map<String, String> estados(List<String> pedidoIds) {
        Map<String, String> r = cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri(BASE + "/estados")
                .headers(cliente.comoServicio()).body(pedidoIds).retrieve()
                .body(new ParameterizedTypeReference<Map<String, String>>() {
                }));
        return r == null ? Map.of() : r;
    }

    /** HU-050: el domiciliario entregó; el pedido pasa a ENTREGADO para poder cobrarlo. Idempotente. */
    public void entregar(String pedidoId) {
        cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri(BASE + "/{id}/entrega", pedidoId)
                .headers(cliente.comoServicio()).retrieve().toBodilessEntity());
    }

    /** HU-047 y HU-052: cancela (restituye inventario) o anula sin factura. Idempotente. */
    public void anular(String pedidoId, String motivo) {
        cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri(BASE + "/{id}/anulacion", pedidoId)
                .headers(cliente.comoServicio()).body(Map.of("motivo", motivo)).retrieve().toBodilessEntity());
    }
}
