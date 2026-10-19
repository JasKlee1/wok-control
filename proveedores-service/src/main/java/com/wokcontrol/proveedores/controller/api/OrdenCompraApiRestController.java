package com.wokcontrol.proveedores.controller.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.proveedores.dto.ProveedorDtos.CancelacionRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.EntregaRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.OrdenRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.OrdenResponse;
import com.wokcontrol.proveedores.dto.ProveedorDtos.RecepcionRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ResumenCompras;
import com.wokcontrol.proveedores.entity.OrdenCompra;
import com.wokcontrol.proveedores.service.OrdenCompraService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ordenes-compra")
@Tag(name = "Órdenes de compra", description = "Solicitudes de abastecimiento, seguimiento y recepción de mercancía")
public class OrdenCompraApiRestController {

    private final OrdenCompraService servicio;

    public OrdenCompraApiRestController(OrdenCompraService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar órdenes", description = "Filtros por estado y proveedor")
    public List<OrdenResponse> listar(@RequestParam(required = false) OrdenCompra.Estado estado,
            @RequestParam(required = false) String proveedorId) {
        return servicio.listar(estado, proveedorId).stream().map(OrdenResponse::de).toList();
    }

    @GetMapping("/resumen")
    @Operation(summary = "Indicadores de compras", description = "Órdenes pendientes y compras recibidas en el mes")
    public ResumenCompras resumen() {
        return servicio.resumen();
    }

    @GetMapping("/mias")
    @Operation(summary = "Órdenes del proveedor autenticado", description = "Rol Proveedor")
    public List<OrdenResponse> mias() {
        return servicio.mias().stream().map(OrdenResponse::de).toList();
    }

    @GetMapping("/mias/{id}")
    @Operation(summary = "Detalle de una orden propia (rol Proveedor)")
    public OrdenResponse mia(@PathVariable String id) {
        return OrdenResponse.de(servicio.miOrden(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar orden")
    public OrdenResponse obtener(@PathVariable String id) {
        return OrdenResponse.de(servicio.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear orden en borrador")
    public ResponseEntity<OrdenResponse> crear(@Valid @RequestBody OrdenRequest req) {
        OrdenResponse r = OrdenResponse.de(servicio.crear(req));
        return ResponseEntity.created(URI.create("/api/ordenes-compra/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar orden en borrador")
    public OrdenResponse actualizar(@PathVariable String id, @Valid @RequestBody OrdenRequest req) {
        return OrdenResponse.de(servicio.actualizar(id, req));
    }

    @PostMapping("/{id}/envio")
    @Operation(summary = "Enviar al proveedor (BORRADOR → ENVIADA)")
    public OrdenResponse enviar(@PathVariable String id) {
        return OrdenResponse.de(servicio.enviar(id));
    }

    @PostMapping("/{id}/confirmacion")
    @Operation(summary = "Confirmar orden (ENVIADA → CONFIRMADA)", description = "Proveedor dueño de la orden o compras")
    public OrdenResponse confirmar(@PathVariable String id) {
        return OrdenResponse.de(servicio.confirmar(id));
    }

    @PostMapping("/{id}/entrega")
    @Operation(summary = "Reportar entrega (CONFIRMADA → ENTREGA_REPORTADA)", description = "No modifica inventario")
    public OrdenResponse entrega(@PathVariable String id, @Valid @RequestBody EntregaRequest req) {
        return OrdenResponse.de(servicio.reportarEntrega(id, req));
    }

    @PostMapping("/{id}/recepcion")
    @Operation(summary = "Confirmar recepción de mercancía", description = "Registra las cantidades realmente recibidas y las "
            + "ingresa a inventario de forma idempotente (OC-id). Si inventario no responde queda pendiente de sincronizar.")
    public OrdenResponse recibir(@PathVariable String id, @Valid @RequestBody RecepcionRequest req) {
        return OrdenResponse.de(servicio.recibir(id, req.lineas(), req.observaciones()));
    }

    @PostMapping("/{id}/recepcion/reintento")
    @Operation(summary = "Reintentar sincronización de la recepción con inventario")
    public OrdenResponse reintentar(@PathVariable String id) {
        return OrdenResponse.de(servicio.reintentarSincronizacion(id));
    }

    @PostMapping("/{id}/cancelacion")
    @Operation(summary = "Cancelar orden", description = "Desde borrador, enviada o confirmada, con motivo")
    public OrdenResponse cancelar(@PathVariable String id, @Valid @RequestBody CancelacionRequest req) {
        return OrdenResponse.de(servicio.cancelar(id, req.motivo()));
    }
}
