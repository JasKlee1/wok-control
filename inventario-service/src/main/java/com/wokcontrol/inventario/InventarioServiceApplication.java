package com.wokcontrol.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Insumos, lotes, movimientos trazables, mermas, ajustes y alertas de stock.
 */
@SpringBootApplication(scanBasePackages = { "com.wokcontrol.inventario", "com.wokcontrol.common" })
public class InventarioServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventarioServiceApplication.class, args);
    }
}
