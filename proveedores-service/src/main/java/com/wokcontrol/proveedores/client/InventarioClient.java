package com.wokcontrol.proveedores.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/** Insumos (para asociarlos y pedirlos) y registro idempotente de recepciones en inventario-service. */
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

    public record LineaRecepcion(String insumoId, BigDecimal cantidad, BigDecimal costoUnitario, String lote,
            LocalDate fechaVencimiento) {
    }

    public record RecepcionRequest(String referencia, String proveedorId, List<LineaRecepcion> lineas) {
    }

    public record ResultadoOperacion(String referencia, String estado, boolean yaProcesada, int lineas) {
    }

    public List<InsumoResumen> insumos() {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get().uri("/api/insumos/resumen")
                .headers(cliente.comoServicio()).retrieve()
                .body(new ParameterizedTypeReference<List<InsumoResumen>>() {
                }));
    }

    public ResultadoOperacion registrarRecepcion(RecepcionRequest req) {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri("/api/inventario/recepciones")
                .headers(cliente.comoServicio()).body(req).retrieve().body(ResultadoOperacion.class));
    }
}
