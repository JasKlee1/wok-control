package com.wokcontrol.facturacion.controller.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.facturacion.dto.FacturacionDtos.AutorizacionRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.AutorizacionResponse;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CierreRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CierreResponse;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PreliminarCierre;
import com.wokcontrol.facturacion.service.CierreCajaService;
import com.wokcontrol.facturacion.service.DescuentoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Caja y descuentos", description = "Cierres de caja y autorizaciones de descuento")
public class CajaApiRestController {

    private final CierreCajaService cierres;
    private final DescuentoService descuentos;

    public CajaApiRestController(CierreCajaService cierres, DescuentoService descuentos) {
        this.cierres = cierres;
        this.descuentos = descuentos;
    }

    @GetMapping("/api/caja/cierres/preliminar")
    @Operation(summary = "Vista previa del cierre del cajero autenticado", description = "Totales por método de pago según el sistema")
    public PreliminarCierre preliminar() {
        return cierres.preliminar(UsuarioActual.requerido().responsable());
    }

    @PostMapping("/api/caja/cierres")
    @Operation(summary = "Registrar cierre de caja", description = "Compara lo contado con lo registrado; una diferencia exige observaciones")
    public ResponseEntity<CierreResponse> cerrar(@Valid @RequestBody CierreRequest req) {
        CierreResponse r = CierreResponse.de(cierres.cerrar(req.declarado(), req.observaciones()));
        return ResponseEntity.created(URI.create("/api/caja/cierres/" + r.id())).body(r);
    }

    @GetMapping("/api/caja/cierres")
    @Operation(summary = "Cierres recientes")
    public List<CierreResponse> listar() {
        return cierres.recientes().stream().map(CierreResponse::de).toList();
    }

    @GetMapping("/api/caja/cierres/{id}")
    @Operation(summary = "Consultar cierre")
    public CierreResponse obtener(@PathVariable String id) {
        return CierreResponse.de(cierres.obtener(id));
    }

    @PostMapping("/api/descuentos/autorizaciones")
    @Operation(summary = "Autorizar descuento", description = "Genera un código de un solo uso con porcentaje máximo y vigencia")
    public ResponseEntity<AutorizacionResponse> autorizar(@Valid @RequestBody AutorizacionRequest req) {
        AutorizacionResponse r = AutorizacionResponse.de(descuentos.autorizar(req));
        return ResponseEntity.status(201).body(r);
    }

    @GetMapping("/api/descuentos/autorizaciones")
    @Operation(summary = "Autorizaciones recientes")
    public List<AutorizacionResponse> autorizaciones() {
        return descuentos.recientes().stream().map(AutorizacionResponse::de).toList();
    }
}
