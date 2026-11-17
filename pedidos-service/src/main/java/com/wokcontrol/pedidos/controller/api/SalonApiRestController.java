package com.wokcontrol.pedidos.controller.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.pedidos.client.UsuariosClient;
import com.wokcontrol.pedidos.dto.PedidoDtos.MesaResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignacionResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignarRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.GrupoRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.GrupoResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.MapaSalon;
import com.wokcontrol.pedidos.entity.GrupoEspera;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.SalonService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** v2 · Épica 8: API del anfitrión (permiso SALON_OPERAR). */
@RestController
@RequestMapping("/api/salon")
@Tag(name = "Salón", description = "Anfitrión: mapa del salón, lista de espera, asignación de mesas y mesas por limpiar")
public class SalonApiRestController {

    private final SalonService salon;
    private final MesaService mesas;

    public SalonApiRestController(SalonService salon, MesaService mesas) {
        this.salon = salon;
        this.mesas = mesas;
    }

    @GetMapping("/mapa")
    @Operation(summary = "Mapa del salón", description = "HU-041: mesas activas con estado (libre, ocupada, por limpiar), capacidad, grupo y mesero; totales y huella")
    public MapaSalon mapa() {
        return salon.mapa();
    }

    @GetMapping("/espera")
    @Operation(summary = "Lista de espera", description = "HU-040: grupos en espera por orden de llegada, con los minutos que llevan esperando")
    public List<GrupoResponse> espera() {
        return salon.esperaRespuesta();
    }

    @GetMapping("/espera/{id}")
    @Operation(summary = "Consultar un grupo")
    public GrupoResponse grupo(@PathVariable String id) {
        return GrupoResponse.de(salon.grupo(id), Instant.now());
    }

    @PostMapping("/espera")
    @Operation(summary = "Registrar un grupo en la lista de espera", description = "AC-040.2: sin nombre o con 0 personas responde 400 indicando el campo")
    public ResponseEntity<GrupoResponse> registrar(@Valid @RequestBody GrupoRequest req) {
        GrupoEspera g = salon.registrar(req);
        return ResponseEntity.created(URI.create("/api/salon/espera/" + g.getId())).body(GrupoResponse.de(g, Instant.now()));
    }

    @PutMapping("/espera/{id}")
    @Operation(summary = "Editar un grupo en espera")
    public GrupoResponse editar(@PathVariable String id, @Valid @RequestBody GrupoRequest req) {
        return GrupoResponse.de(salon.editar(id, req), Instant.now());
    }

    @DeleteMapping("/espera/{id}")
    @Operation(summary = "Retirar un grupo de la lista", description = "El grupo queda RETIRADO; no se borra, para la auditoría")
    public ResponseEntity<Void> retirar(@PathVariable String id, @RequestParam(required = false) String motivo) {
        salon.retirar(id, motivo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/meseros")
    @Operation(summary = "Meseros activos", description = "HU-042: meseros a los que se puede asignar una mesa")
    public List<UsuariosClient.Mesero> meseros() {
        return salon.meseros();
    }

    @PostMapping("/asignaciones")
    @Operation(summary = "Sentar un grupo", description = "HU-042/045: una o varias mesas libres con un mesero. "
            + "Responde 422 con codigo CAPACIDAD_INSUFICIENTE o MESA_NO_DISPONIBLE")
    public ResponseEntity<AsignacionResponse> asignar(@Valid @RequestBody AsignarRequest req) {
        return ResponseEntity.status(201).body(salon.asignar(req));
    }

    @PatchMapping("/mesas/{id}/limpia")
    @Operation(summary = "Marcar una mesa limpia", description = "HU-044: de POR_LIMPIAR a libre (DISPONIBLE)")
    public MesaResponse limpia(@PathVariable String id) {
        return respuesta(mesas.marcarLimpia(id));
    }

    @PatchMapping("/mesas/{id}/liberar")
    @Operation(summary = "El grupo se fue", description = "HU-044: una mesa ocupada sin pedidos activos pasa a POR_LIMPIAR (y su unión se separa)")
    public MesaResponse liberar(@PathVariable String id) {
        return respuesta(mesas.liberar(id));
    }

    private static MesaResponse respuesta(Mesa m) {
        return MesaService.respuesta(m, 0, java.math.BigDecimal.ZERO, 0);
    }
}
