package com.wokcontrol.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.ProductoCarta;
import com.wokcontrol.pedidos.client.MenuClient.SeccionCarta;
import com.wokcontrol.pedidos.config.SecurityConfig;
import com.wokcontrol.pedidos.controller.web.ClienteMesaWebController;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.service.PedidoClienteService;
import com.wokcontrol.pedidos.service.PedidoClienteService.Carrito;
import com.wokcontrol.pedidos.service.SesionMesaService;

import jakarta.servlet.http.Cookie;

/**
 * Épica 7 · T015 (HU-039, NFR-WOK-001): la carta del cliente es liviana y se ve bien en 360 px.
 * <ul>
 * <li>AC-039.1: 50 cargas de una carta de 40 platos; el p95 del servidor queda muy por debajo de 3 s
 * y la página pesa poco, sin Bootstrap. La medición con red 4G simulada sobre Render se hace con
 * {@code scripts/medir-carta.ps1}.</li>
 * <li>AC-039.2: viewport de celular, sin anchos fijos que provoquen desplazamiento horizontal y
 * botones de al menos 44 px.</li>
 * </ul>
 */
@WebMvcTest(controllers = ClienteMesaWebController.class)
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class CartaLivianaTest {

    /** Peso máximo del HTML de la carta de 40 platos (sin fotos, que se piden aparte y diferidas). */
    static final int MAX_BYTES_HTML = 60_000;

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    SesionMesaService sesiones;
    @MockitoBean
    PedidoClienteService cliente;
    @MockitoBean
    MenuClient menu;

    private Cookie mesa5;

    @BeforeEach
    void cartaDe40Platos() {
        SesionMesa s = new SesionMesa();
        s.setId("s1");
        s.setMesaId("m5");
        s.setMesaNumero(5);
        when(sesiones.validar("s1", "m5")).thenReturn(s);
        when(cliente.carrito(any())).thenReturn(new Carrito(List.of(), BigDecimal.ZERO, 0, false));
        when(cliente.huella(any())).thenReturn("h");
        List<SeccionCarta> secciones = new ArrayList<>();
        for (int c = 0; c < 5; c++) {
            int cat = c;
            secciones.add(new SeccionCarta("c" + c, "Categoría " + c, IntStream.range(0, 8)
                    .mapToObj(k -> new ProductoCarta("PLATO", "p" + cat + k, "Plato " + cat + "-" + k,
                            "Descripción del plato con sus ingredientes principales", new BigDecimal("25000"), k % 4,
                            Set.of("SOYA"), "img" + cat + k))
                    .toList()));
        }
        when(menu.carta()).thenReturn(secciones);
        mesa5 = new Cookie("WOK_TOKEN", tokens.emitirTokenMesa("s1", "m5", 5).token());
    }

    @Test
    void ac039_1_cincuentaCargasDeLaCartaDe40PlatosConP95MenorA3Segundos() throws Exception {
        long[] tiempos = new long[50];
        String html = null;
        for (int i = 0; i < tiempos.length; i++) {
            long t0 = System.nanoTime();
            html = mvc.perform(get("/cliente/carta").cookie(mesa5)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            tiempos[i] = (System.nanoTime() - t0) / 1_000_000;
        }
        java.util.Arrays.sort(tiempos);
        long p95 = tiempos[(int) Math.ceil(tiempos.length * 0.95) - 1];

        assertThat(p95).as("p95 del servidor en ms").isLessThan(3000);
        assertThat(html.split("class=\"c-card plato\"", -1).length - 1).isEqualTo(40);
        assertThat(html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(MAX_BYTES_HTML);
        assertThat(html).doesNotContain("bootstrap.min.css");
        assertThat(html).contains("loading=\"lazy\"").contains("decoding=\"async\"").contains("width=\"88\"");
    }

    @Test
    void ac039_2_laCartaSeAdaptaA360pxSinDesplazamientoHorizontal() throws Exception {
        String html = mvc.perform(get("/cliente/carta").cookie(mesa5)).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("name=\"viewport\" content=\"width=device-width, initial-scale=1\"");
        assertThat(html).contains("overflow-x: hidden").contains("overflow-wrap: anywhere");
        assertThat(html).contains("min-height: 44px");
        // ningún ancho fijo mayor que la pantalla de 360 px
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![-\\w])width:\\s*(\\d+)px").matcher(html);
        while (m.find()) {
            assertThat(Integer.parseInt(m.group(1))).as("ancho fijo en px").isLessThanOrEqualTo(360);
        }
    }

    @Test
    void ac039_2_miPedidoTambienEsLivianoYAdaptable() throws Exception {
        String html = mvc.perform(get("/cliente/pedido").cookie(mesa5)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("bootstrap.min.css");
        assertThat(html).contains("name=\"viewport\"").contains("min-height: 44px");
    }
}
