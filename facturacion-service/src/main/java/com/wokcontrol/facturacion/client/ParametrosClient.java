package com.wokcontrol.facturacion.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/**
 * Parámetros del restaurante (nombre, NIT, impuesto al consumo) desde usuarios-service, con caché
 * de un minuto. Si el servicio no responde se usa el último valor conocido o el de configuración.
 */
@Component
public class ParametrosClient {

    private static final Logger log = LoggerFactory.getLogger(ParametrosClient.class);
    private static final Duration VIGENCIA = Duration.ofMinutes(1);

    private final ClienteInterno cliente;
    private final BigDecimal impuestoPorDefecto;
    private volatile Parametros cache;
    private volatile Instant cargadoEn = Instant.EPOCH;

    public ParametrosClient(ClienteInterno cliente,
            @Value("${wok.facturacion.impuesto-consumo-porcentaje:8}") BigDecimal impuestoPorDefecto) {
        this.cliente = cliente;
        this.impuestoPorDefecto = impuestoPorDefecto;
    }

    public record Parametros(String nombreRestaurante, String nit, String direccion, String telefono,
            BigDecimal impuestoConsumoPorcentaje, String mensajeComprobante) {
    }

    public Parametros obtener() {
        if (cache != null && Instant.now().isBefore(cargadoEn.plus(VIGENCIA))) {
            return cache;
        }
        try {
            Parametros p = cliente.llamar("usuarios-service", true, () -> cliente.para("usuarios-service").get()
                    .uri("/api/parametros").headers(cliente.comoServicio()).retrieve().body(Parametros.class));
            if (p != null && p.impuestoConsumoPorcentaje() != null) {
                cache = p;
                cargadoEn = Instant.now();
                return p;
            }
        } catch (RuntimeException ex) {
            log.warn("Parámetros no disponibles, se usa el último valor conocido o el de configuración: {}", ex.getMessage());
        }
        return cache != null ? cache
                : new Parametros("Wok Control", null, null, null, impuestoPorDefecto,
                        "Comprobante interno de venta. No válido como factura electrónica.");
    }
}
