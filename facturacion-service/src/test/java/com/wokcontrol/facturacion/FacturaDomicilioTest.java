package com.wokcontrol.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.facturacion.client.ParametrosClient;
import com.wokcontrol.facturacion.client.PedidosClient;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CobroDomicilioRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PagoRequest;
import com.wokcontrol.facturacion.entity.Factura;
import com.wokcontrol.facturacion.repository.BloqueoPedidoRepository;
import com.wokcontrol.facturacion.repository.FacturaRepository;
import com.wokcontrol.facturacion.service.DescuentoService;
import com.wokcontrol.facturacion.service.FacturacionService;

/** v2 · HU-050 (RN-16): comprobante del cobro contra entrega, a nombre del cajero que registró el domicilio. */
class FacturaDomicilioTest {

    private FacturaRepository repo;
    private PedidosClient pedidos;
    private FacturacionService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(FacturaRepository.class);
        when(repo.save(any(Factura.class))).thenAnswer(i -> {
            Factura f = i.getArgument(0);
            if (f.getId() == null) {
                f.setId("f9");
            }
            return f;
        });
        when(repo.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());
        pedidos = mock(PedidosClient.class);
        when(pedidos.pedido("p1")).thenReturn(pedido("ENTREGADO"));
        ParametrosClient parametros = mock(ParametrosClient.class);
        when(parametros.obtener()).thenReturn(new ParametrosClient.Parametros("Wok", "900", null, null, new BigDecimal("8"), "Gracias"));
        SecuenciaService sec = mock(SecuenciaService.class);
        when(sec.siguienteFormateado(anyString(), anyString())).thenReturn("TIQ-000042");
        servicio = new FacturacionService(repo, mock(BloqueoPedidoRepository.class), pedidos, parametros,
                mock(DescuentoService.class), sec, mock(MongoTemplate.class), mock(AuditoriaPublisher.class), "America/Bogota");

        // llama domicilios-service con token de servicio; el actor es el domiciliario que cobró
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("domicilios-service").claim("tipo", "servicio")
                .claim("actor", "luis").claim("permisos", List.of(Permisos.SISTEMA)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority(Permisos.SISTEMA))));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static PedidosClient.Pedido pedido(String estado) {
        return new PedidosClient.Pedido("p1", "P-000077", null, 0, estado,
                List.of(new PedidosClient.Item("arroz", "Arroz frito especial", new BigDecimal("12500"), 3, new BigDecimal("37500"))),
                new BigDecimal("37500"), Instant.now());
    }

    private static CobroDomicilioRequest cobro(String monto) {
        return new CobroDomicilioRequest("dom1", "D-0001", "p1", new BigDecimal("4500"), "caja1", "Diana Ortiz", "luis",
                "Luis Pabón", List.of(new PagoRequest(Factura.MetodoPago.EFECTIVO, new BigDecimal(monto), null)));
    }

    @Test
    void ac050_1_totalConImpuestoSoloSobrePlatosMasDomicilioYCambio() {
        Factura f = servicio.cobrarDomicilio(cobro("50000"));

        assertThat(f.getSubtotal()).isEqualByComparingTo("37500");
        assertThat(f.getImpuestoValor()).isEqualByComparingTo("3000");
        assertThat(f.getTotal()).isEqualByComparingTo("45000");
        assertThat(f.getCambio()).isEqualByComparingTo("5000");
        assertThat(f.getDomicilio().numero()).isEqualTo("D-0001");
        assertThat(f.getDomicilio().valorDomicilio()).isEqualByComparingTo("4500");
        assertThat(f.getDomicilio().domiciliario()).isEqualTo("luis");
        verify(pedidos).marcarPagado("p1", "f9");
    }

    @Test
    void hu050_elComprobanteQuedaEnLaCajaDelCajeroQueRegistro() {
        Factura f = servicio.cobrarDomicilio(cobro("45000"));
        assertThat(f.getCajero()).isEqualTo("caja1");
        assertThat(f.getCajeroNombre()).isEqualTo("Diana Ortiz");
        assertThat(f.getClaveIdempotencia()).isEqualTo("DOM-dom1");
    }

    @Test
    void esIdempotentePorDomicilio() {
        Factura previa = new Factura();
        previa.setId("f1");
        when(repo.findByClaveIdempotencia("DOM-dom1")).thenReturn(Optional.of(previa));
        assertThat(servicio.cobrarDomicilio(cobro("45000"))).isSameAs(previa);
        verify(pedidos, never()).pedido(anyString());
    }

    @Test
    void ac050_2_pagoInsuficienteOPedidoNoEntregadoNoEmiten() {
        assertThatThrownBy(() -> servicio.cobrarDomicilio(cobro("44000")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("PAGO_INSUFICIENTE"));
        when(pedidos.pedido("p1")).thenReturn(pedido("LISTO"));
        assertThatThrownBy(() -> servicio.cobrarDomicilio(cobro("45000"))).isInstanceOf(ReglaNegocioException.class);
        verify(repo, never()).save(any());
    }
}
