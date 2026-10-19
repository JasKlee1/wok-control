package com.wokcontrol.usuarios.controller.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.common.audit.EventoAuditoria;
import com.wokcontrol.usuarios.dto.ParametrosDtos.AuditoriaResponse;
import com.wokcontrol.usuarios.service.AuditoriaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Auditoría", description = "Registro central de operaciones importantes de todos los servicios")
public class AuditoriaApiRestController {

    private final AuditoriaService servicio;

    public AuditoriaApiRestController(AuditoriaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Registrar evento (interno)", description = "Solo con token de servicio (permiso SISTEMA). Bloqueado en el gateway.")
    public void registrar(@Valid @RequestBody EventoAuditoria evento) {
        servicio.registrar(evento);
    }

    @GetMapping
    @Operation(summary = "Consultar auditoría", description = "Permiso AUDITORIA_VER")
    public Page<AuditoriaResponse> buscar(@RequestParam(required = false) String usuario,
            @RequestParam(required = false) String servicioOrigen, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return servicio.buscar(usuario, servicioOrigen, q, desde, hasta, PageRequest.of(page, size))
                .map(AuditoriaResponse::de);
    }
}
