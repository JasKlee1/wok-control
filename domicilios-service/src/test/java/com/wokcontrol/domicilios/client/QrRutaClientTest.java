package com.wokcontrol.domicilios.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;


/** v2 · ADR-005: integración con la API externa goQR.me (api.qrserver.com), simulada con MockRestServiceServer. */
class QrRutaClientTest {

    private static final String API = "https://api.qrserver.com/v1/create-qr-code/";
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0 };

    private MockRestServiceServer servidor;
    private QrRutaClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new QrRutaClient(builder, API, true, "Girón, Santander, Colombia");
    }

    @Test
    void laRutaApuntaAGoogleMapsConLaCiudadDelRestaurante() {
        String url = cliente.urlRuta("Calle 45 # 13-20");
        assertThat(url).startsWith("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=");
        assertThat(URLDecoder.decode(url.substring(url.indexOf("destination=") + 12), StandardCharsets.UTF_8))
                .isEqualTo("Calle 45 # 13-20, Girón, Santander, Colombia");
        assertThat(cliente.proveedor()).isEqualTo("api.qrserver.com");
    }

    @Test
    void generaElQrConLaUrlCodificadaYLoGuardaEnCache() {
        servidor.expect(once(), requestTo(Matchers.startsWith(API)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("size", "300x300"))
                .andExpect(queryParam("format", "png"))
                .andExpect(queryParam("data", Matchers.containsString("google.com")))
                .andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        assertThat(cliente.qrRuta("D-0001", "Calle 45 # 13-20")).contains(PNG);
        // segunda vez sale de la caché: no hay otra llamada a la API externa
        assertThat(cliente.qrRuta("D-0001", "Calle 45 # 13-20")).contains(PNG);
        servidor.verify();
        // el '#' de la dirección viaja codificado para no cortar la URL
        assertThat(cliente.urlApi("Calle 45 # 13-20").getRawQuery()).doesNotContain("#").contains("%2523");
    }

    @Test
    void siLaApiFallaOResponderAlgoQueNoEsPngSeDevuelveVacio() {
        servidor.expect(once(), requestTo(Matchers.startsWith(API))).andRespond(withServerError());
        assertThat(cliente.qrRuta("D-0002", "Carrera 27 # 30-10")).isEmpty();

        servidor.reset();
        servidor.expect(once(), requestTo(Matchers.startsWith(API))).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));
        assertThat(cliente.qrRuta("D-0003", "Avenida 1 # 2-3")).isEmpty();
    }

    @Test
    void deshabilitadaNoLlamaALaApi() {
        QrRutaClient apagado = new QrRutaClient(RestClient.builder(), API, false, "Girón");
        assertThat(apagado.qrRuta("D-0004", "Calle 1")).isEmpty();
        assertThat(apagado.habilitado()).isFalse();
    }
}
