package com.wokcontrol.menu.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/** Consulta de insumos en inventario-service para construir y validar recetas. */
@Component
public class InventarioClient {

    private static final String SERVICIO = "inventario-service";
    private final ClienteInterno cliente;

    public InventarioClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record InsumoResumen(String id, String nombre, String unidad, String categoria, BigDecimal costoUnitario,
            BigDecimal cantidadDisponible, boolean activo) {
    }

    /** Lista de insumos activos, consultada con token de servicio (la gestión del menú ya exige MENU_GESTIONAR). */
    public List<InsumoResumen> insumosActivos() {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri("/api/insumos/resumen").headers(cliente.comoServicio())
                .retrieve().body(new ParameterizedTypeReference<List<InsumoResumen>>() {
                }));
    }
}
