package com.wokcontrol.domicilios.client;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.wokcontrol.common.client.ClienteInterno;

/** v2 · HU-050: emite en facturacion-service el comprobante del cobro contra entrega. */
@Component
public class FacturacionClient {

    private static final String SERVICIO = "facturacion-service";
    private final ClienteInterno cliente;

    public FacturacionClient(ClienteInterno cliente) {
        this.cliente = cliente;
    }

    public record Pago(String metodo, BigDecimal monto, String referencia) {
    }

    public record CobroDomicilioRequest(String domicilioId, String domicilioNumero, String pedidoId,
            BigDecimal valorDomicilio, String cajero, String cajeroNombre, String domiciliario,
            String domiciliarioNombre, List<Pago> pagos) {
    }

    public record Comprobante(String id, String numero, BigDecimal total, BigDecimal totalRecibido, BigDecimal cambio,
            String cajero) {
    }

    /** Idempotente por domicilio: si se repite, facturación devuelve el mismo comprobante. */
    public Comprobante cobrar(CobroDomicilioRequest req) {
        return cliente.llamar(SERVICIO, true, () -> cliente.para(SERVICIO).post().uri("/api/facturas/domicilios")
                .headers(cliente.comoServicio()).body(req).retrieve().body(Comprobante.class));
    }
}
