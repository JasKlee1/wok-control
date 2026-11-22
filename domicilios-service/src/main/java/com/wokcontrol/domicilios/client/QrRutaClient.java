package com.wokcontrol.domicilios.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * v2 · CR-001 · <b>API externa</b> (ADR-005): genera el código QR de la ruta del domicilio con la API
 * pública de goQR.me ({@code https://api.qrserver.com/v1/create-qr-code/}). El QR lleva un enlace de
 * Google Maps con la ruta hasta la dirección del cliente: el domiciliario lo escanea con el celular y
 * el mapa abre la navegación.
 *
 * <ul>
 * <li>La llamada la hace el servidor (no el navegador), con tiempo máximo de 5 s, y se guarda en una
 * caché en memoria: el mismo QR no se pide dos veces.</li>
 * <li>Si la API no responde o devuelve algo que no es un PNG, se devuelve vacío y la pantalla muestra el
 * enlace «Abrir ruta» en su lugar: la entrega nunca depende del servicio externo.</li>
 * <li>Ley 1581: solo viaja la dirección y la ciudad (nunca el nombre ni el teléfono) y el registro no
 * la escribe; solo el número del domicilio, el tamaño de la imagen y el tiempo de respuesta.</li>
 * </ul>
 */
@Component
public class QrRutaClient {

    private static final Logger log = LoggerFactory.getLogger(QrRutaClient.class);
    private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G' };
    private static final int MAX_CACHE = 200;

    private final RestClient http;
    private final String urlApi;
    private final boolean habilitado;
    private final String ciudad;
    private final Map<String, byte[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_CACHE;
        }
    });

    @Autowired
    public QrRutaClient(@Value("${wok.domicilios.qr.url:https://api.qrserver.com/v1/create-qr-code/}") String urlApi,
            @Value("${wok.domicilios.qr.habilitado:true}") boolean habilitado,
            @Value("${wok.domicilios.ciudad:Girón, Santander, Colombia}") String ciudad,
            @Value("${wok.domicilios.qr.timeout:5s}") Duration timeout) {
        this(RestClient.builder().requestFactory(fabrica(timeout)), urlApi, habilitado, ciudad);
    }

    /** Para pruebas: permite enlazar un servidor simulado al constructor del cliente HTTP. */
    QrRutaClient(RestClient.Builder builder, String urlApi, boolean habilitado, String ciudad) {
        this.http = builder.build();
        this.urlApi = urlApi;
        this.habilitado = habilitado;
        this.ciudad = ciudad;
    }

    private static JdkClientHttpRequestFactory fabrica(Duration timeout) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(cliente);
        f.setReadTimeout(timeout);
        return f;
    }

    /** Enlace de Google Maps con la ruta hasta la dirección; es lo que va dentro del QR. */
    public String urlRuta(String direccion) {
        String destino = direccion == null ? "" : direccion.trim();
        if (ciudad != null && !ciudad.isBlank()) {
            destino = destino + ", " + ciudad.trim();
        }
        return "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination="
                + URLEncoder.encode(destino, StandardCharsets.UTF_8);
    }

    /** URL completa de la llamada a la API externa (se muestra en Swagger y en la demo). */
    public URI urlApi(String direccion) {
        return UriComponentsBuilder.fromUriString(urlApi)
                .queryParam("size", "300x300").queryParam("margin", "8").queryParam("ecc", "M")
                .queryParam("format", "png").queryParam("data", "{data}")
                .encode().buildAndExpand(urlRuta(direccion)).toUri();
    }

    public String proveedor() {
        return URI.create(urlApi).getHost();
    }

    public boolean habilitado() {
        return habilitado;
    }

    /**
     * PNG del QR de la ruta, o vacío si la API está deshabilitada o no respondió bien.
     *
     * @param numeroDomicilio solo para el registro (no se escribe la dirección)
     */
    public Optional<byte[]> qrRuta(String numeroDomicilio, String direccion) {
        if (!habilitado || direccion == null || direccion.isBlank()) {
            return Optional.empty();
        }
        String clave = urlRuta(direccion);
        byte[] guardado = cache.get(clave);
        if (guardado != null) {
            return Optional.of(guardado);
        }
        long inicio = System.nanoTime();
        try {
            byte[] png = http.get().uri(urlApi(direccion)).accept(MediaType.IMAGE_PNG).retrieve().body(byte[].class);
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            if (!esPng(png)) {
                log.warn("API externa {}: la respuesta para el domicilio {} no es un PNG; se muestra el enlace de la ruta",
                        proveedor(), numeroDomicilio);
                return Optional.empty();
            }
            cache.put(clave, png);
            log.info("API externa {}: QR de la ruta del domicilio {} generado ({} bytes, {} ms)", proveedor(),
                    numeroDomicilio, png.length, ms);
            return Optional.of(png);
        } catch (RestClientException ex) {
            log.warn("API externa {} no disponible para el domicilio {}: {}. Se muestra el enlace de la ruta.", proveedor(),
                    numeroDomicilio, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static boolean esPng(byte[] b) {
        if (b == null || b.length < FIRMA_PNG.length) {
            return false;
        }
        for (int i = 0; i < FIRMA_PNG.length; i++) {
            if (b[i] != FIRMA_PNG[i]) {
                return false;
            }
        }
        return true;
    }
}
