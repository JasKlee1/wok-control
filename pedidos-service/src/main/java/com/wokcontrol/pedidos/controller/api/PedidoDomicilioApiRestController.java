package com.wokcontrol.pedidos.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.pedidos.dto.PedidoDtos.MotivoRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoDomicilioRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoResponse;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.service.PedidoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * v2 · CR-001 (épica 9): operaciones que domicilios-service hace sobre el pedido de cocina de un
 * domicilio. Son internas: solo con token de servicio (permiso SISTEMA) y el gateway las bloquea.
 */
@RestController
@RequestMapping("/api/pedidos/internos/domicilios")
@Tag(name = "Pedidos a domicilio (interno)", description = "Solo domicilios-service con token de servicio")
public class PedidoDomicilioApiRestController {

    private final PedidoService pedidos;

    public PedidoDomicilioApiRestController(PedidoService pedidos) {
        this.pedidos = pedidos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar el pedido de un domicilio y enviarlo a cocina (HU-047)",
            description = "Idempotente por domicilioId. Si falta stock, responde 422 y el pedido no llega a cocina.")
    public PedidoResponse crear(@Valid @RequestBody PedidoDomicilioRequest req) {
        return PedidoResponse.de(pedidos.crearDomicilio(req));
    }

    @PostMapping("/estados")
    @Operation(summary = "Estado en cocina de varios pedidos (HU-048/049)", description = "Cuerpo: lista de ids de pedido")
    public Map<String, EstadoPedido> estados(@RequestBody List<String> ids) {
        return pedidos.estados(ids);
    }

    @PostMapping("/{id}/entrega")
    @Operation(summary = "Marcar entregado por el domiciliario (HU-050)", description = "Idempotente. 422 PEDIDO_NO_LISTO si no está listo.")
    public PedidoResponse entrega(@PathVariable String id) {
        return PedidoResponse.de(pedidos.entregarDomicilio(id));
    }

    @PostMapping("/{id}/anulacion")
    @Operation(summary = "Cancelar o anular sin factura (HU-047, HU-052)",
            description = "Si cocina no ha empezado se cancela y se restituye el inventario; si no, se anula sin factura. Idempotente.")
    public PedidoResponse anulacion(@PathVariable String id, @Valid @RequestBody MotivoRequest req) {
        return PedidoResponse.de(pedidos.anularDomicilio(id, req.motivo()));
    }
}
