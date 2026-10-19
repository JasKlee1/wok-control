package com.wokcontrol.proveedores.controller.api;

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

import com.wokcontrol.proveedores.dto.ProveedorDtos.HistorialProveedor;
import com.wokcontrol.proveedores.dto.ProveedorDtos.PerfilProveedorRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ProveedorRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ProveedorResponse;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ProveedorResumen;
import com.wokcontrol.proveedores.service.OrdenCompraService;
import com.wokcontrol.proveedores.service.ProveedorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/proveedores")
@Tag(name = "Proveedores", description = "Registro de proveedores, insumos suministrados y condiciones comerciales")
public class ProveedorApiRestController {

    private final ProveedorService servicio;
    private final OrdenCompraService ordenes;

    public ProveedorApiRestController(ProveedorService servicio, OrdenCompraService ordenes) {
        this.servicio = servicio;
        this.ordenes = ordenes;
    }

    @GetMapping
    @Operation(summary = "Buscar proveedores", description = "Activos e inactivos; filtros por texto, categoría e insumo")
    public List<ProveedorResponse> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo, @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String insumoId) {
        return servicio.buscar(q, activo, categoria, insumoId).stream().map(ProveedorResponse::de).toList();
    }

    @GetMapping("/resumen")
    @Operation(summary = "Resumen de proveedores", description = "Para otros servicios: vincular usuarios o ver proveedores de un insumo")
    public List<ProveedorResumen> resumen(@RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String insumoId) {
        return servicio.buscar(null, activo, null, insumoId).stream().map(ProveedorResumen::de).toList();
    }

    @GetMapping("/mi-perfil")
    @Operation(summary = "Perfil del proveedor autenticado", description = "Rol Proveedor; solo devuelve su propia información")
    public ProveedorResponse miPerfil() {
        return ProveedorResponse.de(servicio.miPerfil());
    }

    @PutMapping("/mi-perfil")
    @Operation(summary = "Actualizar datos de contacto propios", description = "El proveedor no puede cambiar NIT, razón social ni condiciones")
    public ProveedorResponse actualizarMiPerfil(@Valid @RequestBody PerfilProveedorRequest req) {
        return ProveedorResponse.de(servicio.actualizarMiPerfil(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar proveedor")
    public ProveedorResponse obtener(@PathVariable String id) {
        return ProveedorResponse.de(servicio.obtener(id));
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Historial de compras al proveedor")
    public HistorialProveedor historial(@PathVariable String id) {
        servicio.obtener(id);
        return ordenes.historial(id);
    }

    @PostMapping
    @Operation(summary = "Registrar proveedor", description = "El NIT es único; los insumos se validan con inventario-service")
    public ResponseEntity<ProveedorResponse> crear(@Valid @RequestBody ProveedorRequest req) {
        ProveedorResponse r = ProveedorResponse.de(servicio.crear(req));
        return ResponseEntity.created(URI.create("/api/proveedores/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar proveedor")
    public ProveedorResponse actualizar(@PathVariable String id, @Valid @RequestBody ProveedorRequest req) {
        return ProveedorResponse.de(servicio.actualizar(id, req));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar o desactivar proveedor")
    public ProveedorResponse estado(@PathVariable String id, @RequestParam boolean activo) {
        return ProveedorResponse.de(servicio.cambiarEstado(id, activo));
    }
}
