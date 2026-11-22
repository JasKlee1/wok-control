package com.wokcontrol.domicilios.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/**
 * v2 · HU-048: domiciliarios activos en usuarios-service (solo usuario y nombre). Usa el token de
 * servicio porque el cajero no gestiona usuarios.
 */
@Component
public class UsuariosClient {

    private static final String SERVICIO = "usuarios-service";
    private final ClienteInterno cliente;

    public UsuariosClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record Persona(String username, String nombre) {
    }

    public List<Persona> domiciliarios() {
        Persona[] r = cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri("/api/usuarios/internos/domiciliarios").headers(cliente.comoServicio()).retrieve().body(Persona[].class));
        return r == null ? List.of() : List.of(r);
    }
}
