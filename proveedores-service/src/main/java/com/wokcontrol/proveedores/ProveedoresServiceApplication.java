package com.wokcontrol.proveedores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Proveedores, condiciones comerciales y órdenes de compra.
 */
@SpringBootApplication(scanBasePackages = { "com.wokcontrol.proveedores", "com.wokcontrol.common" })
public class ProveedoresServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProveedoresServiceApplication.class, args);
    }
}
