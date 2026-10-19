package com.wokcontrol.usuarios.controller.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.usuarios.dto.ParametrosDtos.ParametrosRequest;
import com.wokcontrol.usuarios.dto.ParametrosDtos.ParametrosResponse;
import com.wokcontrol.usuarios.service.ParametroService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/parametros")
@Tag(name = "Parámetros generales", description = "Datos del restaurante e impuesto al consumo")
public class ParametroApiRestController {

    private final ParametroService servicio;

    public ParametroApiRestController(ParametroService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Consultar parámetros", description = "Cualquier usuario autenticado o servicio interno")
    public ParametrosResponse obtener() {
        return ParametrosResponse.de(servicio.obtener());
    }

    @PutMapping
    @Operation(summary = "Actualizar parámetros", description = "Permiso PARAMETROS_GESTIONAR")
    public ParametrosResponse actualizar(@Valid @RequestBody ParametrosRequest req) {
        return ParametrosResponse.de(servicio.actualizar(req, UsuarioActual.responsableActual()));
    }
}
