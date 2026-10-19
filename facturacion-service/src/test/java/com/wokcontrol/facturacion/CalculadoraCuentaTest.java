package com.wokcontrol.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PagoRequest;
import com.wokcontrol.facturacion.entity.Factura;
import com.wokcontrol.facturacion.entity.Factura.MetodoPago;
import com.wokcontrol.facturacion.service.CalculadoraCuenta;

class CalculadoraCuentaTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void calculaDescuentoEImpuestoAlConsumoEnPesos() {
        CalculadoraCuenta.Totales t = CalculadoraCuenta.calcular(bd("75000"), bd("10"), bd("8"));
        assertThat(t.subtotal()).isEqualByComparingTo("75000");
        assertThat(t.descuento()).isEqualByComparingTo("7500");
        assertThat(t.base()).isEqualByComparingTo("67500");
        assertThat(t.impuesto()).isEqualByComparingTo("5400");
        assertThat(t.total()).isEqualByComparingTo("72900");
    }

    @Test
    void redondeaAPesosSinDecimales() {
        CalculadoraCuenta.Totales t = CalculadoraCuenta.calcular(bd("12345"), bd("7.5"), bd("8"));
        assertThat(t.descuento()).isEqualByComparingTo("926");      // 925.875
        assertThat(t.impuesto()).isEqualByComparingTo("914");       // 11419 × 8% = 913.52
        assertThat(t.total()).isEqualByComparingTo("12333");
        assertThat(t.total().scale()).isZero();
    }

    @Test
    void rechazaDescuentosFueraDeRangoYCuentasVacias() {
        assertThatThrownBy(() -> CalculadoraCuenta.calcular(bd("1000"), bd("60"), bd("8"))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> CalculadoraCuenta.calcular(BigDecimal.ZERO, null, bd("8"))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void elEfectivoAdmiteCambioPeroLosPagosElectronicosNo() {
        CalculadoraCuenta.ResultadoPagos r = CalculadoraCuenta.validarPagos(
                List.of(new PagoRequest(MetodoPago.TARJETA_DEBITO, bd("50000"), "AUT123"),
                        new PagoRequest(MetodoPago.EFECTIVO, bd("30000"), null)), bd("72900"));
        assertThat(r.recibido()).isEqualByComparingTo("80000");
        assertThat(r.cambio()).isEqualByComparingTo("7100");

        assertThatThrownBy(() -> CalculadoraCuenta.validarPagos(
                List.of(new PagoRequest(MetodoPago.TRANSFERENCIA, bd("80000"), null)), bd("72900")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("electrónicos");
    }

    @Test
    void pagosInsuficientesSonRechazados() {
        assertThatThrownBy(() -> CalculadoraCuenta.validarPagos(
                List.of(new PagoRequest(MetodoPago.EFECTIVO, bd("70000"), null)), bd("72900")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no cubren");
    }

    @Test
    void netoEnEfectivoDescuentaElCambio() {
        Factura f = new Factura();
        f.setPagos(List.of(new Factura.Pago(MetodoPago.EFECTIVO, bd("100000"), null, "APROBADO"),
                new Factura.Pago(MetodoPago.TARJETA_CREDITO, bd("20000"), null, "APROBADO")));
        f.setCambio(bd("7100"));
        f.setEmitidaEn(Instant.now());
        assertThat(f.netoPorMetodo(MetodoPago.EFECTIVO)).isEqualByComparingTo("92900");
        assertThat(f.netoPorMetodo(MetodoPago.TARJETA_CREDITO)).isEqualByComparingTo("20000");
    }
}
