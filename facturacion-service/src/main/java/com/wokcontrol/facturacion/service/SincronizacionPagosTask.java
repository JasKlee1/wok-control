package com.wokcontrol.facturacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wokcontrol.facturacion.entity.Factura;

/**
 * Reintento periódico de comprobantes cuya notificación a pedidos-service falló. Solo reintenta
 * operaciones idempotentes y se detiene tras 30 intentos (queda visible para gestión manual).
 */
@Component
public class SincronizacionPagosTask {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionPagosTask.class);
    private static final int MAX_INTENTOS = 30;

    private final FacturacionService servicio;

    public SincronizacionPagosTask(FacturacionService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${wok.facturacion.reintento-ms:60000}", initialDelayString = "${wok.facturacion.reintento-inicial-ms:30000}")
    public void reintentar() {
        try {
            for (Factura f : servicio.pendientesSincronizar()) {
                if (f.getIntentosSincronizacion() < MAX_INTENTOS) {
                    servicio.sincronizarPagos(f);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Reintento de sincronización de pagos no completado: {}", ex.getMessage());
        }
    }
}
