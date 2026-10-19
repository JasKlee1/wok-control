package com.wokcontrol.facturacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PagoRequest;
import com.wokcontrol.facturacion.entity.Factura;

/**
 * Cálculos monetarios en pesos colombianos (sin decimales, redondeo HALF_UP). Reglas:
 * <ol>
 * <li>Subtotal = suma de cantidad × precio copiado en cada pedido.</li>
 * <li>Descuento = subtotal × porcentaje (máximo 50%).</li>
 * <li>Impuesto al consumo = (subtotal − descuento) × porcentaje vigente. Los precios de la carta se
 * registran antes de impuesto.</li>
 * <li>Total = base + impuesto. Los pagos deben cubrir el total; solo el efectivo admite excedente,
 * que se devuelve como cambio.</li>
 * </ol>
 */
public final class CalculadoraCuenta {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraCuenta() {
    }

    public record Totales(BigDecimal subtotal, BigDecimal descuento, BigDecimal base, BigDecimal impuesto, BigDecimal total) {
    }

    public record ResultadoPagos(BigDecimal recibido, BigDecimal cambio) {
    }

    public static BigDecimal pesos(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    public static Totales calcular(BigDecimal subtotalBruto, BigDecimal porcentajeDescuento, BigDecimal porcentajeImpuesto) {
        if (subtotalBruto == null || subtotalBruto.signum() <= 0) {
            throw new ReglaNegocioException("La cuenta no tiene valor a cobrar");
        }
        BigDecimal subtotal = pesos(subtotalBruto);
        BigDecimal pDesc = porcentajeDescuento == null ? BigDecimal.ZERO : porcentajeDescuento;
        if (pDesc.signum() < 0 || pDesc.compareTo(new BigDecimal("50")) > 0) {
            throw new ReglaNegocioException("El descuento debe estar entre 0% y 50%");
        }
        BigDecimal descuento = pesos(subtotal.multiply(pDesc).divide(CIEN, 4, RoundingMode.HALF_UP));
        BigDecimal base = subtotal.subtract(descuento);
        BigDecimal pImp = porcentajeImpuesto == null ? BigDecimal.ZERO : porcentajeImpuesto;
        BigDecimal impuesto = pesos(base.multiply(pImp).divide(CIEN, 4, RoundingMode.HALF_UP));
        return new Totales(subtotal, descuento, base, impuesto, base.add(impuesto));
    }

    public static ResultadoPagos validarPagos(List<PagoRequest> pagos, BigDecimal total) {
        if (pagos == null || pagos.isEmpty()) {
            throw new ReglaNegocioException("Registre al menos un pago");
        }
        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal otros = BigDecimal.ZERO;
        for (PagoRequest p : pagos) {
            if (p.monto() == null || p.monto().signum() <= 0) {
                throw new ReglaNegocioException("Cada pago debe tener un monto positivo");
            }
            BigDecimal monto = pesos(p.monto());
            if (p.metodo() == Factura.MetodoPago.EFECTIVO) {
                efectivo = efectivo.add(monto);
            } else {
                otros = otros.add(monto);
            }
        }
        if (otros.compareTo(total) > 0) {
            throw new ReglaNegocioException("Los pagos electrónicos superan el total de la cuenta");
        }
        BigDecimal recibido = efectivo.add(otros);
        if (recibido.compareTo(total) < 0) {
            throw new ReglaNegocioException("Los pagos ($" + recibido.toPlainString() + ") no cubren el total ($"
                    + total.toPlainString() + ")");
        }
        return new ResultadoPagos(recibido, recibido.subtract(total));
    }
}
