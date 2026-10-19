package com.wokcontrol.common.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.ServicioRemotoException;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.common.security.UsuarioActual;

/**
 * Punto único para llamar a otros microservicios por REST: resuelve el servicio por Eureka, agrega
 * la autenticación adecuada, traduce los errores remotos a excepciones de negocio y reintenta solo
 * operaciones idempotentes ante fallos de conexión.
 */
@Component
public class ClienteInterno {

    private static final Logger log = LoggerFactory.getLogger(ClienteInterno.class);
    private static final int INTENTOS_IDEMPOTENTES = 2;

    private final RestClient.Builder builder;
    private final TokenService tokenService;
    private final String servicioLocal;

    public ClienteInterno(@Qualifier("restClientBuilderInterno") RestClient.Builder builder, TokenService tokenService,
            @Value("${spring.application.name}") String servicioLocal) {
        this.builder = builder;
        this.tokenService = tokenService;
        this.servicioLocal = servicioLocal;
    }

    public RestClient para(String servicio) {
        return builder.clone().baseUrl("http://" + servicio).build();
    }

    /** Reenvía el JWT del usuario actual: el servicio destino aplica sus permisos. */
    public Consumer<HttpHeaders> comoUsuario() {
        String token = UsuarioActual.opcional().map(UsuarioActual::token)
                .orElseThrow(() -> new IllegalStateException("No hay usuario autenticado para reenviar su token"));
        return h -> h.setBearerAuth(token);
    }

    /** Token interno con permiso SISTEMA; conserva como actor al usuario que originó la operación. */
    public Consumer<HttpHeaders> comoServicio() {
        String token = tokenService.emitirTokenServicio(servicioLocal, UsuarioActual.responsableActual());
        return h -> h.setBearerAuth(token);
    }

    public <T> T llamar(String servicio, boolean idempotente, Supplier<T> llamada) {
        int intentos = idempotente ? INTENTOS_IDEMPOTENTES : 1;
        for (int i = 1;; i++) {
            try {
                return llamada.get();
            } catch (HttpClientErrorException ex) {
                throw traducir(servicio, ex);
            } catch (HttpServerErrorException ex) {
                log.warn("{} respondió {}: {}", servicio, ex.getStatusCode(), ex.getMessage());
                throw new ServicioRemotoException(servicio, "El servicio " + servicio + " no está disponible ("
                        + ex.getStatusCode().value() + ")", ex);
            } catch (ResourceAccessException ex) {
                if (i < intentos) {
                    log.info("Reintentando llamada idempotente a {} tras fallo de conexión", servicio);
                    continue;
                }
                throw new ServicioRemotoException(servicio, "No fue posible comunicarse con " + servicio, ex);
            } catch (RestClientException ex) {
                throw new ServicioRemotoException(servicio, "Respuesta inválida de " + servicio, ex);
            }
        }
    }

    private RuntimeException traducir(String servicio, HttpClientErrorException ex) {
        String detalle = detalle(ex);
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            return new ServicioRemotoException(servicio, detalle, ex);
        }
        return switch (status) {
            case NOT_FOUND -> new RecursoNoEncontradoException(detalle);
            case CONFLICT -> new ConflictoException(detalle);
            case FORBIDDEN -> new OperacionNoPermitidaException(detalle);
            case BAD_REQUEST, UNPROCESSABLE_CONTENT -> new ReglaNegocioException(detalle);
            default -> new ServicioRemotoException(servicio, servicio + ": " + detalle, ex);
        };
    }

    private static String detalle(HttpClientErrorException ex) {
        try {
            ProblemDetail pd = ex.getResponseBodyAs(ProblemDetail.class);
            if (pd != null && pd.getDetail() != null) {
                return pd.getDetail();
            }
        } catch (RuntimeException ignorada) {
            // cuerpo no es ProblemDetail
        }
        return ex.getStatusText();
    }
}
