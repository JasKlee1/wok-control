package com.wokcontrol.facturacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Cuentas, pagos, comprobantes internos de venta, descuentos y cierres de caja.
 */
@SpringBootApplication(scanBasePackages = { "com.wokcontrol.facturacion", "com.wokcontrol.common" })
public class FacturacionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacturacionServiceApplication.class, args);
    }
}
