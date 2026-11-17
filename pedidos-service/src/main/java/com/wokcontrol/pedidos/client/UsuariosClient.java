package com.wokcontrol.pedidos.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/**
 * v2 · HU-042: consulta en usuarios-service los meseros activos a los que el anfitrión puede
 * asignar una mesa. Usa el token de servicio porque el anfitrión no gestiona usuarios.
 */
@Component
public class UsuariosClient {

    private static final String SERVICIO = "usuarios-service";
    private final ClienteInterno cliente;

    public UsuariosClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record Mesero(String username, String nombre) {
    }

    /** Meseros activos y no bloqueados, ordenados por nombre (endpoint interno de usuarios-service). */
    public List<Mesero> meseros() {
        Mesero[] r = cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri("/api/usuarios/internos/meseros").headers(cliente.comoServicio()).retrieve().body(Mesero[].class));
        return r == null ? List.of() : List.of(r);
    }
}
