package com.wokcontrol.menu.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wokcontrol.menu.dto.MenuDtos.CotizacionRequest;
import com.wokcontrol.menu.dto.MenuDtos.CotizacionResponse;
import com.wokcontrol.menu.dto.MenuDtos.SeccionCarta;
import com.wokcontrol.menu.service.CartaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/menu")
@Tag(name = "Carta", description = "Carta disponible para tomar pedidos y cotización de productos")
public class CartaApiRestController {

    private final CartaService servicio;

    public CartaApiRestController(CartaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/carta")
    @Operation(summary = "Carta disponible", description = "Solo categorías activas con platos y combos disponibles")
    public List<SeccionCarta> carta() {
        return servicio.cartaDisponible();
    }

    @PostMapping("/cotizacion")
    @Operation(summary = "Cotizar productos", description = "Usado por pedidos-service: devuelve precio vigente, "
            + "disponibilidad y receta en insumos por unidad. No modifica datos.")
    public CotizacionResponse cotizar(@Valid @RequestBody CotizacionRequest req) {
        return servicio.cotizar(req);
    }
}
