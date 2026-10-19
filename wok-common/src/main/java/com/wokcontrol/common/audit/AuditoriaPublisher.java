package com.wokcontrol.common.audit;

/**
 * Registro de operaciones importantes (quién, qué, sobre qué, cuándo) para la auditoría central.
 */
public interface AuditoriaPublisher {

    void registrar(String accion, String entidad, String entidadId, String detalle);
}
