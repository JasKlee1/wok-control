package com.wokcontrol.usuarios.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.audit.EventoAuditoria;
import com.wokcontrol.common.security.UsuarioActual;

/**
 * usuarios-service custodia la auditoría central, así que registra sus propios eventos directamente.
 */
@Component
@ConditionalOnProperty(name = "wok.auditoria.modo", havingValue = "local")
public class LocalAuditoriaPublisher implements AuditoriaPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalAuditoriaPublisher.class);
    private final AuditoriaService servicio;

    public LocalAuditoriaPublisher(AuditoriaService servicio) {
        this.servicio = servicio;
    }

    @Override
    public void registrar(String accion, String entidad, String entidadId, String detalle) {
        try {
            servicio.registrar(new EventoAuditoria("usuarios-service", UsuarioActual.responsableActual(), accion, entidad,
                    entidadId, detalle, Instant.now()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar la auditoría {} {} {}: {}", accion, entidad, entidadId, ex.getMessage());
        }
    }
}
