package com.wokcontrol.reportes.client;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;
import com.wokcontrol.reportes.dto.ReportesDtos.Alertas;
import com.wokcontrol.reportes.dto.ReportesDtos.InsumoResumen;
import com.wokcontrol.reportes.dto.ReportesDtos.Plato;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenCompras;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenPedidos;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenVentas;
import com.wokcontrol.reportes.dto.ReportesDtos.Valorizacion;

/**
 * Consultas de solo lectura a los demás servicios. Se reenvía el JWT del usuario: cada servicio
 * aplica sus propios permisos, de modo que el panel nunca muestra datos que el usuario no puede ver.
 */
@Component
public class ServiciosClient {

    private final ClienteInterno cliente;

    public ServiciosClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public Consumer<HttpHeaders> credenciales() {
        return cliente.comoUsuario();
    }

    public ResumenVentas ventas(Consumer<HttpHeaders> auth, LocalDate desde, LocalDate hasta) {
        return get("facturacion-service", auth, ResumenVentas.class,
                "/api/facturas/resumen?desde=" + desde + "&hasta=" + hasta);
    }

    public ResumenPedidos pedidos(Consumer<HttpHeaders> auth) {
        return get("pedidos-service", auth, ResumenPedidos.class, "/api/pedidos/resumen");
    }

    public Alertas alertas(Consumer<HttpHeaders> auth) {
        return get("inventario-service", auth, Alertas.class, "/api/inventario/alertas");
    }

    public Valorizacion valorizacion(Consumer<HttpHeaders> auth) {
        return get("inventario-service", auth, Valorizacion.class, "/api/inventario/valorizacion");
    }

    public ResumenCompras compras(Consumer<HttpHeaders> auth) {
        return get("proveedores-service", auth, ResumenCompras.class, "/api/ordenes-compra/resumen");
    }

    public List<Plato> platos(Consumer<HttpHeaders> auth) {
        return cliente.llamar("menu-service", true, () -> cliente.para("menu-service").get().uri("/api/menu/platos")
                .headers(auth).retrieve().body(new ParameterizedTypeReference<List<Plato>>() {
                }));
    }

    public List<InsumoResumen> insumos(Consumer<HttpHeaders> auth) {
        return cliente.llamar("inventario-service", true, () -> cliente.para("inventario-service").get()
                .uri("/api/insumos/resumen").headers(auth).retrieve()
                .body(new ParameterizedTypeReference<List<InsumoResumen>>() {
                }));
    }

    private <T> T get(String servicio, Consumer<HttpHeaders> auth, Class<T> tipo, String uri) {
        return cliente.llamar(servicio, true, () -> cliente.para(servicio).get().uri(uri).headers(auth).retrieve().body(tipo));
    }
}
