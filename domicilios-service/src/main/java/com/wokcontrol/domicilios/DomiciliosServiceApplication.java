package com.wokcontrol.domicilios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.wokcontrol.domicilios.config.ValidacionArranque;

/**
 * v2 · CR-001 (épica 9): pedidos a domicilio por teléfono, asignación al domiciliario, entrega y
 * cobro contra entrega. Tiene su propia base (wok_domicilios) y habla con pedidos-service (cocina),
 * facturacion-service (comprobante), usuarios-service (domiciliarios) y menu-service (carta).
 */
@SpringBootApplication(scanBasePackages = { "com.wokcontrol.domicilios", "com.wokcontrol.common" })
public class DomiciliosServiceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DomiciliosServiceApplication.class);
        // AC-053.3: sin la cadena de conexión de MongoDB el servicio no arranca y lo dice sin mostrar secretos
        app.addListeners(new ValidacionArranque());
        app.run(args);
    }
}
