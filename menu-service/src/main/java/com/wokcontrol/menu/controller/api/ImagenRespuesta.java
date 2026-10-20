package com.wokcontrol.menu.controller.api;

import java.time.Duration;
import java.util.Optional;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mongodb.gridfs.GridFsResource;

/**
 * Respuesta HTTP de una foto de GridFS, compartida por la API y la interfaz web. Cada foto nueva
 * recibe un identificador nuevo, así que el navegador puede guardarla en caché por largo tiempo.
 */
public final class ImagenRespuesta {

    private ImagenRespuesta() {
    }

    public static ResponseEntity<?> de(Optional<GridFsResource> recurso) {
        if (recurso.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GridFsResource r = recurso.get();
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
                    .contentType(MediaType.parseMediaType(r.getContentType()))
                    .contentLength(r.contentLength())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(r.getInputStream()));
        } catch (java.io.IOException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
