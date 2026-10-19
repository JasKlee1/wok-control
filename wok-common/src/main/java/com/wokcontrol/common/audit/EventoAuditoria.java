package com.wokcontrol.common.audit;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Evento enviado a usuarios-service, que custodia la auditoría central. */
public record EventoAuditoria(
        @NotBlank @Size(max = 60) String servicio,
        @NotBlank @Size(max = 80) String usuario,
        @NotBlank @Size(max = 60) String accion,
        @NotBlank @Size(max = 60) String entidad,
        @Size(max = 80) String entidadId,
        @Size(max = 500) String detalle,
        Instant fecha) {
}
