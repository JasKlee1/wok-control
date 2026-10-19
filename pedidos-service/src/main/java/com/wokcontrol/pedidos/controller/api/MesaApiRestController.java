package com.wokcontrol.pedidos.controller.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.pedidos.dto.PedidoDtos.MesaRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.MesaResponse;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoResponse;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mesas")
@Tag(name = "Mesas", description = "Mesas del restaurante y su ocupación")
public class MesaApiRestController {

    private final MesaService mesas;
    private final PedidoService pedidos;

    public MesaApiRestController(MesaService mesas, PedidoService pedidos) {
        this.mesas = mesas;
        this.pedidos = pedidos;
    }

    @GetMapping
    @Operation(summary = "Tablero de mesas", description = "Estado, pedidos activos y consumo acumulado")
    public List<MesaResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return mesas.tablero(incluirInactivas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar mesa")
    public MesaResponse obtener(@PathVariable String id) {
        return mesas.tablero(true).stream().filter(m -> m.id().equals(id)).findFirst()
                .orElseThrow(() -> new com.wokcontrol.common.exception.RecursoNoEncontradoException("Mesa", id));
    }

    @GetMapping("/{id}/pedidos")
    @Operation(summary = "Pedidos activos de la mesa")
    public List<PedidoResponse> pedidosActivos(@PathVariable String id) {
        return pedidos.activosDeMesa(id).stream().map(PedidoResponse::de).toList();
    }

    @PostMapping
    @Operation(summary = "Crear mesa", description = "Permiso MESAS_GESTIONAR")
    public ResponseEntity<MesaResponse> crear(@Valid @RequestBody MesaRequest req) {
        Mesa m = mesas.crear(req);
        return ResponseEntity.created(URI.create("/api/mesas/" + m.getId())).body(obtener(m.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar mesa")
    public MesaResponse actualizar(@PathVariable String id, @Valid @RequestBody MesaRequest req) {
        mesas.actualizar(id, req);
        return obtener(id);
    }

    @PatchMapping("/{id}/liberar")
    @Operation(summary = "Liberar mesa", description = "Solo si no tiene pedidos activos")
    public MesaResponse liberar(@PathVariable String id) {
        mesas.liberar(id);
        return obtener(id);
    }

    @PatchMapping("/{id}/servicio")
    @Operation(summary = "Poner o quitar fuera de servicio")
    public MesaResponse servicio(@PathVariable String id, @RequestParam boolean fueraDeServicio) {
        mesas.fueraDeServicio(id, fueraDeServicio);
        return obtener(id);
    }
}
