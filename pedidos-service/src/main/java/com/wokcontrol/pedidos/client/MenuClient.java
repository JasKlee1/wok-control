package com.wokcontrol.pedidos.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/** Consulta de la carta y cotización de productos en menu-service. */
@Component
public class MenuClient {

    private static final String SERVICIO = "menu-service";
    private final ClienteInterno cliente;

    public MenuClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record ItemCotizar(String tipo, String id, int cantidad) {
    }

    public record CotizacionRequest(List<ItemCotizar> items) {
    }

    public record ConsumoInsumo(String insumoId, String nombreInsumo, String unidad, BigDecimal cantidadPorUnidad) {
    }

    public record ItemCotizado(String tipo, String id, String nombre, BigDecimal precioUnitario, boolean disponible,
            String motivoNoDisponible, int picanteBase, Set<String> alergenos, List<ConsumoInsumo> receta) {
    }

    public record CotizacionResponse(List<ItemCotizado> items, boolean todosDisponibles) {
    }

    public record ProductoCarta(String tipo, String id, String nombre, String descripcion, BigDecimal precio,
            int picanteBase, Set<String> alergenos, String imagenId) {
    }

    public record SeccionCarta(String categoriaId, String categoria, List<ProductoCarta> productos) {
    }

    /** Cotización con token de servicio (actor = mesero): precio vigente, disponibilidad y receta. */
    public CotizacionResponse cotizar(List<ItemCotizar> items) {
        CotizacionResponse r = cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post()
                .uri("/api/menu/cotizacion").headers(cliente.comoServicio())
                .body(new CotizacionRequest(items)).retrieve().body(CotizacionResponse.class));
        if (r == null || r.items() == null || r.items().size() != items.size()) {
            throw new com.wokcontrol.common.exception.ServicioRemotoException(SERVICIO,
                    "Respuesta de cotización incompleta de menu-service", null);
        }
        return r;
    }

    public List<SeccionCarta> carta() {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri("/api/menu/carta").headers(cliente.comoServicio())
                .retrieve().body(new ParameterizedTypeReference<List<SeccionCarta>>() {
                }));
    }
}
