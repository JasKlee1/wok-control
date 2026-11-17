package com.wokcontrol.pedidos.dto;

import java.time.Instant;
import java.util.List;

import com.wokcontrol.pedidos.dto.PedidoDtos.MesaResponse;
import com.wokcontrol.pedidos.entity.GrupoEspera;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** v2 · Épica 8: anfitrión, lista de espera y mapa del salón. */
public final class SalonDtos {

    private SalonDtos() {
    }

    public static final String MSG_NOMBRE = "Indica el nombre del grupo";
    public static final String MSG_PERSONAS = "Indica cuántas personas son (mínimo 1)";

    @Schema(description = "Grupo de la lista de espera. Si no se envía la hora de llegada, se toma la actual")
    public record GrupoRequest(
            @NotBlank(message = MSG_NOMBRE) @Size(max = 60, message = "Máximo 60 caracteres") String nombre,
            @NotNull(message = MSG_PERSONAS) @Min(value = 1, message = MSG_PERSONAS) @Max(value = 50, message = "Máximo 50 personas") Integer personas,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Hora en formato HH:mm") String llegada,
            @Size(max = 200, message = "Máximo 200 caracteres") String notas) {
    }

    public record GrupoResponse(String id, String nombre, int personas, Instant llegadaEn, long minutosEspera,
            String notas, GrupoEspera.Estado estado, List<Integer> mesaNumeros, String mesero, String meseroNombre,
            Instant sentadoEn) {

        public static GrupoResponse de(GrupoEspera g, Instant ahora) {
            return new GrupoResponse(g.getId(), g.getNombre(), g.getPersonas(), g.getLlegadaEn(), g.minutosEspera(ahora),
                    g.getNotas(), g.getEstado(), g.getMesaNumeros(), g.getMesero(), g.getMeseroNombre(), g.getSentadoEn());
        }
    }

    @Schema(description = "Sentar un grupo: de la lista (grupoId) o uno que llega y encuentra mesa (nombre y personas). "
            + "Con varias mesas se unen (HU-045)")
    public record AsignarRequest(
            String grupoId,
            @Size(max = 60) String nombre,
            @Min(value = 1, message = MSG_PERSONAS) @Max(50) Integer personas,
            @NotEmpty(message = "Elige al menos una mesa") List<String> mesaIds,
            @NotBlank(message = "Elige el mesero que atenderá la mesa") String mesero) {
    }

    public record AsignacionResponse(GrupoResponse grupo, List<MesaResponse> mesas, int puestos, boolean union) {
    }

    /** v2 · HU-041/046: mesas activas con su estado, los totales y la huella para el mapa en vivo. */
    public record MapaSalon(List<MesaResponse> mesas, long libres, long ocupadas, long porLimpiar, long fueraDeServicio,
            int enEspera, String huella) {
    }
}
