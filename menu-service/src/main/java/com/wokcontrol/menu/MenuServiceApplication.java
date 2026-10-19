package com.wokcontrol.menu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Carta: categorías, platos, combos, recetas, precios y disponibilidad.
 */
@SpringBootApplication(scanBasePackages = { "com.wokcontrol.menu", "com.wokcontrol.common" })
public class MenuServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MenuServiceApplication.class, args);
    }
}
