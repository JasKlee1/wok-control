package com.wokcontrol.pedidos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mesas, pedidos, comandas y estados de cocina.
 */
@SpringBootApplication(scanBasePackages = { "com.wokcontrol.pedidos", "com.wokcontrol.common" })
public class PedidosServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PedidosServiceApplication.class, args);
    }
}
