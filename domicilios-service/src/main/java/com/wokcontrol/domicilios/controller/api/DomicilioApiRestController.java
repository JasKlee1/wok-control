package com.wokcontrol.domicilios.controller.api;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.domicilios.dto.DomicilioDtos.AsignacionRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.CobroRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.Domiciliario;
import com.wokcontrol.domicilios.dto.DomicilioDtos.DomicilioRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.DomicilioResponse;
import com.wokcontrol.domicilios.dto.DomicilioDtos.EdicionRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.MotivoRequest;
import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.service.DomicilioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/domicilios")
@Tag(name = "Domicilios", description = "v2 · CR-001: pedidos a domicilio, asignación, entrega, cobro contra entrega y QR de la ruta")
public class DomicilioApiRestController {

    private final DomicilioService servicio;

    public DomicilioApiRestController(DomicilioService servicio) {
        this.servicio = servicio;
    }

    private DomicilioResponse respuesta(Domicilio d) {
        return DomicilioResponse.de(d, servicio.urlRuta(d));
    }

    @GetMapping
    @Operation(summary = "Domicilios en curso (cajero)", description = "Con el avance de cocina al día")
    public List<DomicilioResponse> activos() {
        return servicio.activos().stream().map(this::respuesta).toList();
    }

    @GetMapping("/mios")
    @Operation(summary = "Mis entregas (domiciliario, HU-049)", description = "Solo los asignados y en camino del usuario del token")
    public List<DomicilioResponse> mios() {
        return servicio.mios(UsuarioActual.requerido().responsable()).stream().map(this::respuesta).toList();
    }

    @GetMapping("/domiciliarios")
    @Operation(summary = "Domiciliarios activos con su disponibilidad (HU-048)")
    public List<Domiciliario> domiciliarios() {
        return servicio.domiciliarios();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un domicilio (HU-053)", description = "El domiciliario solo puede consultar los suyos (403 si no)")
    public DomicilioResponse obtener(@PathVariable String id) {
        return respuesta(servicio.obtenerPara(id, UsuarioActual.requerido()));
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Historial de estados (AC-053.2)", description = "Usuario, fecha-hora UTC, estado anterior y nuevo")
    public List<Domicilio.CambioEstado> historial(@PathVariable String id) {
        return servicio.historial(id, UsuarioActual.requerido());
    }

    @GetMapping(value = "/{id}/qr-ruta", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "QR de la ruta en Google Maps (API externa goQR.me)",
            description = "El servidor llama a https://api.qrserver.com/v1/create-qr-code/ con el enlace de la ruta y devuelve el PNG. "
                    + "503 si la API externa no responde (la pantalla muestra el enlace en su lugar).")
    @ApiResponse(responseCode = "200", description = "PNG del código QR", content = @Content(mediaType = "image/png"))
    @ApiResponse(responseCode = "503", description = "La API externa no respondió")
    public ResponseEntity<byte[]> qrRuta(@PathVariable String id) {
        return servicio.qrRuta(id, UsuarioActual.requerido())
                .map(png -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate()).body(png))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    @PostMapping
    @Operation(summary = "Registrar un domicilio (HU-047)", description = "El pedido va a cocina. 400 con errores.telefono o errores.direccion")
    @ApiResponse(responseCode = "201", description = "Domicilio RECIBIDO y pedido en cocina")
    public ResponseEntity<DomicilioResponse> registrar(@Valid @RequestBody DomicilioRequest req) {
        DomicilioResponse r = respuesta(servicio.registrar(req));
        return ResponseEntity.created(URI.create("/api/domicilios/" + r.id())).body(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar antes del despacho (HU-047)", description = "422 YA_DESPACHADO si ya salió")
    public DomicilioResponse editar(@PathVariable String id, @Valid @RequestBody EdicionRequest req) {
        return respuesta(servicio.editar(id, req));
    }

    @PostMapping("/{id}/cancelacion")
    @Operation(summary = "Cancelar antes del despacho (HU-047)")
    public DomicilioResponse cancelar(@PathVariable String id, @Valid @RequestBody MotivoRequest req) {
        return respuesta(servicio.cancelar(id, req.motivo()));
    }

    @PostMapping("/{id}/asignacion")
    @Operation(summary = "Asignar a un domiciliario (HU-048)", description = "422 PEDIDO_NO_LISTO si sigue en cocina")
    public DomicilioResponse asignar(@PathVariable String id, @Valid @RequestBody AsignacionRequest req) {
        return respuesta(servicio.asignar(id, req.domiciliario()));
    }

    @PostMapping("/{id}/en-camino")
    @Operation(summary = "Marcar en camino (HU-049)")
    public DomicilioResponse enCamino(@PathVariable String id) {
        return respuesta(servicio.enCamino(id, UsuarioActual.requerido()));
    }

    @PostMapping("/{id}/entrega")
    @Operation(summary = "Entregar y cobrar contra entrega (HU-049, HU-050)",
            description = "Emite el comprobante en facturación. 422 TRANSICION_INVALIDA si no está en camino; PAGO_INSUFICIENTE si no cubre el total")
    public DomicilioResponse entregar(@PathVariable String id, @Valid @RequestBody CobroRequest req) {
        return respuesta(servicio.entregar(id, req, UsuarioActual.requerido()));
    }

    @PostMapping("/{id}/no-entregado")
    @Operation(summary = "Marcar no entregado (HU-052)", description = "422 MOTIVO_OBLIGATORIO con menos de 10 caracteres")
    public DomicilioResponse noEntregado(@PathVariable String id, @RequestBody MotivoRequest req) {
        return respuesta(servicio.noEntregado(id, req.motivo(), UsuarioActual.requerido()));
    }
}
