package com.wokcontrol.usuarios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Usuarios, roles, autenticación JWT, parámetros y auditoría central.
 */
@SpringBootApplication(scanBasePackages = { "com.wokcontrol.usuarios", "com.wokcontrol.common" })
public class UsuariosServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsuariosServiceApplication.class, args);
    }
}
