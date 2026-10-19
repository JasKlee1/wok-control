package com.wokcontrol.common.audit;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.common.security.UsuarioActual;

/**
 * Envía los eventos de auditoría a usuarios-service de forma asíncrona. Es de mejor esfuerzo: si el
 * envío falla, el evento queda en el log del servicio con nivel WARN (no bloquea la operación de
 * negocio). Los datos del usuario se capturan antes de cambiar de hilo.
 */
@Component
@ConditionalOnProperty(name = "wok.auditoria.modo", havingValue = "remoto", matchIfMissing = true)
public class RemoteAuditoriaPublisher implements AuditoriaPublisher {

    private static final Logger log = LoggerFactory.getLogger(RemoteAuditoriaPublisher.class);

    private final ClienteInterno cliente;
    private final TokenService tokenService;
    private final TaskExecutor executor;
    private final String servicio;

    public RemoteAuditoriaPublisher(ClienteInterno cliente, TokenService tokenService,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor,
            @Value("${spring.application.name}") String servicio) {
        this.cliente = cliente;
        this.tokenService = tokenService;
        this.executor = executor;
        this.servicio = servicio;
    }

    @Override
    public void registrar(String accion, String entidad, String entidadId, String detalle) {
        String usuario = UsuarioActual.responsableActual();
        EventoAuditoria evento = new EventoAuditoria(servicio, usuario, accion, entidad, entidadId,
                detalle == null ? null : detalle.substring(0, Math.min(detalle.length(), 500)), Instant.now());
        String token = tokenService.emitirTokenServicio(servicio, usuario);
        executor.execute(() -> {
            try {
                cliente.para("usuarios-service").post().uri("/api/auditoria/eventos")
                        .headers(h -> h.setBearerAuth(token))
                        .body(evento).retrieve().toBodilessEntity();
            } catch (RuntimeException ex) {
                log.warn("Auditoría no enviada [{} {} {} por {}]: {}", accion, entidad, entidadId, usuario, ex.getMessage());
            }
        });
    }
}
