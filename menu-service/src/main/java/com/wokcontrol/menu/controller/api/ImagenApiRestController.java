package com.wokcontrol.menu.controller.api;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.menu.entity.Imagen;
import com.wokcontrol.menu.service.ImagenService;
import com.wokcontrol.menu.service.ImagenService.Credito;
import com.wokcontrol.menu.service.ImagenService.CreditoListado;
import com.wokcontrol.menu.service.ImagenService.Destino;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/menu")
@Tag(name = "Imágenes", description = "Fotos de platos, combos y categorías guardadas en MongoDB (GridFS)")
public class ImagenApiRestController {

    private final ImagenService servicio;

    public ImagenApiRestController(ImagenService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/imagenes/{imagenId}")
    @Operation(summary = "Descargar una foto")
    public ResponseEntity<?> descargar(@PathVariable String imagenId) {
        return ImagenRespuesta.de(servicio.abrir(imagenId));
    }

    @GetMapping("/imagenes/creditos")
    @Operation(summary = "Autor y licencia de cada foto en uso")
    public List<CreditoListado> creditos() {
        return servicio.creditos();
    }

    @PutMapping(path = "/{destino}/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Asignar o reemplazar la foto", description = "JPG, PNG o WebP de máximo 2 MB. "
            + "Para fotos con licencia libre indique autor, licencia y página de origen.")
    public Imagen asignar(@Parameter(description = "platos, combos o categorias") @PathVariable String destino,
            @PathVariable String id, @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(required = false) String autor, @RequestParam(required = false) String licencia,
            @RequestParam(required = false) String licenciaUrl, @RequestParam(required = false) String fuenteUrl)
            throws IOException {
        return servicio.asignar(destino(destino), id, archivo.getBytes(), new Credito(autor, licencia, licenciaUrl, fuenteUrl));
    }

    @DeleteMapping("/{destino}/{id}/imagen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar la foto")
    public void quitar(@PathVariable String destino, @PathVariable String id) {
        servicio.quitar(destino(destino), id);
    }

    static Destino destino(String ruta) {
        return switch (ruta) {
            case "platos" -> Destino.PLATO;
            case "combos" -> Destino.COMBO;
            case "categorias" -> Destino.CATEGORIA;
            default -> throw new ReglaNegocioException("Destino desconocido: " + ruta);
        };
    }
}
