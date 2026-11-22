package com.wokcontrol.domicilios.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * v2 · HU-053 (AC-053.3): revisa la configuración antes de crear el contexto. Si falta la cadena de
 * conexión de MongoDB (MONGODB_URI en el grupo wok-secretos de Render o en el .env local), el servicio
 * no arranca y el registro lo dice con un mensaje claro. Nunca escribe el valor de la cadena, que lleva
 * usuario y contraseña.
 *
 * <p>Corre después de que Spring cargó el .env y la configuración del config-server, porque esos
 * procesadores tienen mayor prioridad que este listener.</p>
 */
public class ValidacionArranque implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ValidacionArranque.class);

    public static final String MENSAJE = "domicilios-service no arranca: falta la cadena de conexión de MongoDB "
            + "(variable MONGODB_URI en el grupo wok-secretos de Render o en el archivo .env). Revise la configuración; "
            + "el valor no se muestra en el registro.";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        verificar(event.getEnvironment());
    }

    /** Lanza {@link IllegalStateException} si la cadena de conexión no está; nunca incluye su valor. */
    public static void verificar(Environment env) {
        String uri;
        try {
            uri = env.getProperty("spring.mongodb.uri");
        } catch (IllegalArgumentException placeholderSinValor) {
            // ${MONGODB_URI} sin valor: no se propaga el mensaje original para no filtrar nada de la configuración
            uri = null;
        }
        if (uri == null || uri.isBlank() || !(uri.startsWith("mongodb://") || uri.startsWith("mongodb+srv://"))) {
            log.error(MENSAJE);
            throw new IllegalStateException(MENSAJE);
        }
        log.info("Cadena de conexión de MongoDB configurada (valor oculto)");
    }
}
