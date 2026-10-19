package com.wokcontrol.usuarios.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.wokcontrol.usuarios.entity.ParametrosGenerales;
import com.wokcontrol.usuarios.entity.RegistroAuditoria;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ParametrosDtos {

    private ParametrosDtos() {
    }

    public record ParametrosRequest(
            @NotBlank(message = "El nombre del restaurante es obligatorio") @Size(max = 100) String nombreRestaurante,
            @Pattern(regexp = "^$|^[0-9.\\-]{5,20}$", message = "NIT inválido") String nit,
            @Size(max = 150) String direccion,
            @Pattern(regexp = "^$|^[0-9+ ()\\-]{7,20}$", message = "Teléfono inválido") String telefono,
            @NotNull(message = "Indique el porcentaje") @DecimalMin(value = "0", message = "No puede ser negativo")
            @DecimalMax(value = "30", message = "Máximo 30%") BigDecimal impuestoConsumoPorcentaje,
            @Size(max = 250) String mensajeComprobante,
            @Min(value = 5, message = "Mínimo 5 minutos") @Max(value = 120, message = "Máximo 120 minutos") int minutosAlertaCocina) {
    }

    public record ParametrosResponse(String nombreRestaurante, String nit, String direccion, String telefono,
            BigDecimal impuestoConsumoPorcentaje, String mensajeComprobante, int minutosAlertaCocina,
            String actualizadoPor, Instant actualizadoEn) {

        public static ParametrosResponse de(ParametrosGenerales p) {
            return new ParametrosResponse(p.getNombreRestaurante(), p.getNit(), p.getDireccion(), p.getTelefono(),
                    p.getImpuestoConsumoPorcentaje(), p.getMensajeComprobante(), p.getMinutosAlertaCocina(),
                    p.getActualizadoPor(), p.getActualizadoEn());
        }
    }

    public record AuditoriaResponse(String id, String servicio, String usuario, String accion, String entidad,
            String entidadId, String detalle, Instant fecha) {

        public static AuditoriaResponse de(RegistroAuditoria r) {
            return new AuditoriaResponse(r.getId(), r.getServicio(), r.getUsuario(), r.getAccion(), r.getEntidad(),
                    r.getEntidadId(), r.getDetalle(), r.getFecha());
        }
    }
}
