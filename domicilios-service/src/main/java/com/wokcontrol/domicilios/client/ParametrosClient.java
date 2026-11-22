package com.wokcontrol.domicilios.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/**
 * Impuesto al consumo vigente (parámetros de usuarios-service), con caché de un minuto. Es el mismo
 * porcentaje que usa facturación; si el servicio no responde, se usa el último conocido o el de
 * configuración.
 */
@Component
public class ParametrosClient {

    private static final Logger log = LoggerFactory.getLogger(ParametrosClient.class);
    private static final Duration VIGENCIA = Duration.ofMinutes(1);

    private final ClienteInterno cliente;
    private final BigDecimal impuestoPorDefecto;
    private volatile BigDecimal cache;
    private volatile Instant cargadoEn = Instant.EPOCH;

    public ParametrosClient(ClienteInterno cliente,
            @Value("${wok.facturacion.impuesto-consumo-porcentaje:8}") BigDecimal impuestoPorDefecto) {
        this.cliente = cliente;
        this.impuestoPorDefecto = impuestoPorDefecto;
    }

    record Parametros(BigDecimal impuestoConsumoPorcentaje) {
    }

    public BigDecimal impuestoConsumoPorcentaje() {
        if (cache != null && Instant.now().isBefore(cargadoEn.plus(VIGENCIA))) {
            return cache;
        }
        try {
            Parametros p = cliente.llamar("usuarios-service", true, () -> cliente.para("usuarios-service").get()
                    .uri("/api/parametros").headers(cliente.comoServicio()).retrieve().body(Parametros.class));
            if (p != null && p.impuestoConsumoPorcentaje() != null) {
                cache = p.impuestoConsumoPorcentaje();
                cargadoEn = Instant.now();
                return cache;
            }
        } catch (RuntimeException ex) {
            log.warn("Parámetros no disponibles, se usa el último impuesto conocido o el de configuración: {}", ex.getMessage());
        }
        return cache != null ? cache : impuestoPorDefecto;
    }
}
