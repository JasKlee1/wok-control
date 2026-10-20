package com.wokcontrol.menu.entity;

/**
 * Foto de un plato, combo o categoría. El archivo vive en GridFS (wok_menu.fs.files) y aquí se
 * guarda su identificador junto con el crédito exigido por las licencias libres (autor, licencia y
 * página de origen). Los campos de crédito son opcionales para las fotos propias del restaurante.
 */
public record Imagen(String id, String contentType, String autor, String licencia, String licenciaUrl, String fuenteUrl) {

    public boolean tieneCredito() {
        return autor != null && !autor.isBlank() || licencia != null && !licencia.isBlank();
    }
}
