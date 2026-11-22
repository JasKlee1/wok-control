package com.wokcontrol.domicilios.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/** Carta vigente de menu-service para armar el pedido del domicilio (los precios los fija pedidos-service). */
@Component
public class MenuClient {

    private static final String SERVICIO = "menu-service";
    private final ClienteInterno cliente;

    public MenuClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record ProductoCarta(String tipo, String id, String nombre, String descripcion, BigDecimal precio,
            int picanteBase, Set<String> alergenos, String imagenId) {
    }

    public record SeccionCarta(String categoriaId, String categoria, List<ProductoCarta> productos) {
    }

    public List<SeccionCarta> carta() {
        List<SeccionCarta> r = cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).get()
                .uri("/api/menu/carta").headers(cliente.comoServicio())
                .retrieve().body(new ParameterizedTypeReference<List<SeccionCarta>>() {
                }));
        return r == null ? List.of() : r;
    }
}
