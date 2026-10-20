package com.wokcontrol.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.menu.service.ImagenService;

class ImagenServiceTest {

    private static String tipo(byte[] datos) throws Throwable {
        Method m = ImagenService.class.getDeclaredMethod("tipoContenido", byte[].class);
        m.setAccessible(true);
        try {
            return (String) m.invoke(null, (Object) datos);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void reconoceJpegPngYWebpPorSuFirma() throws Throwable {
        assertThat(tipo(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0 })).isEqualTo("image/jpeg");
        assertThat(tipo(new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0 })).isEqualTo("image/png");
        assertThat(tipo(new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0 })).isEqualTo("image/webp");
    }

    @Test
    void rechazaArchivosQueNoSonImagenes() {
        // un HTML renombrado como .jpg no debe guardarse (se serviría con tipo de imagen)
        assertThatThrownBy(() -> tipo("<script>alert(1)</script>".getBytes()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Formato no admitido");
        assertThatThrownBy(() -> tipo(new byte[0])).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazaImagenesDeMasDe2Mb() {
        byte[] grande = new byte[ImagenService.TAMANO_MAXIMO + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;
        assertThatThrownBy(() -> tipo(grande)).hasMessageContaining("2 MB");
    }
}
