package com.wokcontrol.usuarios.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/**
 * Consulta los proveedores registrados para vincularlos a usuarios con rol PROVEEDOR.
 */
@Component
public class ProveedoresClient {

    private static final Logger log = LoggerFactory.getLogger(ProveedoresClient.class);
    private final ClienteInterno cliente;

    public ProveedoresClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record ProveedorResumen(String id, String nombreComercial, String nit, boolean activo) {
    }

    /** Devuelve lista vacía si proveedores-service no está disponible: el formulario lo indica. */
    public List<ProveedorResumen> listarActivos() {
        try {
            return cliente.llamar("proveedores-service", true, () -> cliente.para("proveedores-service").get()
                    .uri("/api/proveedores/resumen?activo=true").headers(cliente.comoServicio())
                    .retrieve().body(new ParameterizedTypeReference<List<ProveedorResumen>>() {
                    }));
        } catch (RuntimeException ex) {
            log.warn("No fue posible consultar proveedores: {}", ex.getMessage());
            return null;
        }
    }
}
