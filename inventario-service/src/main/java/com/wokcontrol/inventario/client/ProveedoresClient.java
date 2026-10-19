package com.wokcontrol.inventario.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/** Proveedores que suministran un insumo (la relación vive en proveedores-service). */
@Component
public class ProveedoresClient {

    private static final Logger log = LoggerFactory.getLogger(ProveedoresClient.class);
    private static final String SERVICIO = "proveedores-service";
    private final ClienteInterno cliente;

    public ProveedoresClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record ProveedorResumen(String id, String nombreComercial, String nit, String telefono, boolean activo) {
    }

    /** null si el servicio no responde (la vista lo indica sin bloquear la consulta del insumo). */
    public List<ProveedorResumen> deInsumo(String insumoId) {
        try {
            return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                    .uri(u -> u.path("/api/proveedores/resumen").queryParam("insumoId", insumoId).build())
                    .headers(cliente.comoServicio())
                    .retrieve().body(new ParameterizedTypeReference<List<ProveedorResumen>>() {
                    }));
        } catch (RuntimeException ex) {
            log.warn("No fue posible consultar proveedores del insumo {}: {}", insumoId, ex.getMessage());
            return null;
        }
    }
}
