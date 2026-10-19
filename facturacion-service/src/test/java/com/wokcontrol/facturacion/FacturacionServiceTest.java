package com.wokcontrol.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.ServicioRemotoException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.facturacion.client.ParametrosClient;
import com.wokcontrol.facturacion.client.PedidosClient;
import com.wokcontrol.facturacion.dto.FacturacionDtos.CobroRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.DescuentoRequest;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PagoRequest;
import com.wokcontrol.facturacion.entity.AutorizacionDescuento;
import com.wokcontrol.facturacion.entity.BloqueoPedido;
import com.wokcontrol.facturacion.entity.Factura;
import com.wokcontrol.facturacion.entity.Factura.MetodoPago;
import com.wokcontrol.facturacion.repository.BloqueoPedidoRepository;
import com.wokcontrol.facturacion.repository.FacturaRepository;
import com.wokcontrol.facturacion.service.DescuentoService;
import com.wokcontrol.facturacion.service.FacturacionService;

class FacturacionServiceTest {

    private FacturaRepository repo;
    private PedidosClient pedidos;
    private DescuentoService descuentos;
    private MongoTemplate mongo;
    private FacturacionService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(FacturaRepository.class);
        when(repo.save(any(Factura.class))).thenAnswer(i -> {
            Factura f = i.getArgument(0);
            if (f.getId() == null) {
                f.setId("f1");
            }
            return f;
        });
        when(repo.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());
        pedidos = mock(PedidosClient.class);
        when(pedidos.mesa("m1")).thenReturn(new PedidosClient.Mesa("m1", 4, "OCUPADA", 1, new BigDecimal("75000")));
        when(pedidos.facturables("m1")).thenReturn(List.of(new PedidosClient.Pedido("p1", "P-000001", "m1", 4, "ENTREGADO",
                List.of(new PedidosClient.Item("arroz", "Arroz frito", new BigDecimal("25000"), 3, new BigDecimal("75000"))),
                new BigDecimal("75000"), Instant.now())));
        ParametrosClient parametros = mock(ParametrosClient.class);
        when(parametros.obtener()).thenReturn(new ParametrosClient.Parametros("Wok", "900", null, null, new BigDecimal("8"), "Gracias"));
        descuentos = mock(DescuentoService.class);
        mongo = mock(MongoTemplate.class);
        SecuenciaService sec = mock(SecuenciaService.class);
        when(sec.siguienteFormateado(anyString(), anyString())).thenReturn("TIQ-000001");
        servicio = new FacturacionService(repo, mock(BloqueoPedidoRepository.class), pedidos, parametros, descuentos, sec,
                mongo, mock(AuditoriaPublisher.class), "America/Bogota");
        autenticar(Roles.CAJERO);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String rol) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("cajero1").claim("nombre", "Luis")
                .claim("roles", List.of(rol)).claim("permisos", List.copyOf(Roles.SISTEMA.get(rol).permisos())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                Roles.SISTEMA.get(rol).permisos().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private static CobroRequest cobro(List<PagoRequest> pagos, DescuentoRequest d) {
        return new CobroRequest("m1", null, pagos, d, "clave-idem-0001");
    }

    @Test
    void cobraConTotalesCalculadosEnServidorYNotificaElPago() {
        Factura f = servicio.cobrar(cobro(List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("100000"), null)), null));

        assertThat(f.getSubtotal()).isEqualByComparingTo("75000");
        assertThat(f.getImpuestoValor()).isEqualByComparingTo("6000");
        assertThat(f.getTotal()).isEqualByComparingTo("81000");
        assertThat(f.getCambio()).isEqualByComparingTo("19000");
        assertThat(f.getSincronizacion()).isEqualTo(Factura.Sincronizacion.COMPLETA);
        verify(pedidos).marcarPagado("p1", "f1");
        verify(mongo).insert(any(BloqueoPedido.class));
    }

    @Test
    void repetirLaMismaSolicitudDevuelveElMismoComprobante() {
        Factura previa = new Factura();
        previa.setId("f0");
        previa.setNumero("TIQ-000009");
        when(repo.findByClaveIdempotencia("clave-idem-0001")).thenReturn(Optional.of(previa));

        Factura f = servicio.cobrar(cobro(List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("100000"), null)), null));

        assertThat(f).isSameAs(previa);
        verify(pedidos, never()).marcarPagado(anyString(), anyString());
        verify(repo, never()).save(any());
    }

    @Test
    void pedidoYaCobradoEnOtroComprobanteGeneraConflicto() {
        when(mongo.insert(any(BloqueoPedido.class))).thenThrow(new DuplicateKeyException("dup"));
        assertThatThrownBy(() -> servicio.cobrar(cobro(List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("90000"), null)), null)))
                .isInstanceOf(ConflictoException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void cajeroNoPuedeDescontarSinCodigoDeAutorizacion() {
        assertThatThrownBy(() -> servicio.cobrar(cobro(List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("90000"), null)),
                new DescuentoRequest(new BigDecimal("10"), "Cliente frecuente", null))))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void descuentoConCodigoValidoSeAplicaYSeVinculaAlComprobante() {
        AutorizacionDescuento a = new AutorizacionDescuento();
        a.setId("a1");
        a.setCodigo("ABC123");
        a.setMotivo("Demora en el servicio");
        a.setAutorizadoPor("admin");
        when(descuentos.consumir("ABC123", new BigDecimal("10"), "m1")).thenReturn(a);

        Factura f = servicio.cobrar(cobro(List.of(new PagoRequest(MetodoPago.TARJETA_DEBITO, new BigDecimal("72900"), "AUT1")),
                new DescuentoRequest(new BigDecimal("10"), null, "ABC123")));

        assertThat(f.getDescuento().valor()).isEqualByComparingTo("7500");
        assertThat(f.getDescuento().autorizadoPor()).isEqualTo("admin");
        assertThat(f.getTotal()).isEqualByComparingTo("72900");
        verify(descuentos).vincularFactura("a1", "f1");
    }

    @Test
    void siElPagoNoCubreElTotalSeLiberaLaAutorizacion() {
        AutorizacionDescuento a = new AutorizacionDescuento();
        a.setId("a1");
        a.setCodigo("ABC123");
        when(descuentos.consumir(anyString(), any(), anyString())).thenReturn(a);
        assertThatThrownBy(() -> servicio.cobrar(cobro(List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("1000"), null)),
                new DescuentoRequest(new BigDecimal("10"), null, "ABC123")))).isInstanceOf(ReglaNegocioException.class);
        verify(descuentos).liberar("a1");
    }

    @Test
    void pedidoNoEntregadoNoSePuedeCobrar() {
        assertThatThrownBy(() -> servicio.cobrar(new CobroRequest("m1", List.of("otro"),
                List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("90000"), null)), null, "clave-idem-0002")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está entregado");
    }

    @Test
    void siPedidosNoRespondeElComprobanteQuedaPendienteDeSincronizar() {
        doThrow(new ServicioRemotoException("pedidos-service", "caído", null)).when(pedidos).marcarPagado(eq("p1"), anyString());

        Factura f = servicio.cobrar(cobro(List.of(new PagoRequest(MetodoPago.EFECTIVO, new BigDecimal("81000"), null)), null));

        assertThat(f.getEstado()).isEqualTo(Factura.Estado.EMITIDA);
        assertThat(f.getSincronizacion()).isEqualTo(Factura.Sincronizacion.PENDIENTE);
        assertThat(f.getIntentosSincronizacion()).isEqualTo(1);
    }

    @Test
    void anularExigeMotivoYNoSePermiteSiYaEstaEnUnCierre() {
        Factura f = new Factura();
        f.setId("f9");
        f.setEstado(Factura.Estado.EMITIDA);
        f.setPedidoIds(List.of("p1"));
        f.setCierreId("c1");
        when(repo.findById("f9")).thenReturn(Optional.of(f));
        assertThatThrownBy(() -> servicio.anular("f9", "Error de digitación")).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("cierre");
        f.setCierreId(null);
        assertThatThrownBy(() -> servicio.anular("f9", "x")).isInstanceOf(ReglaNegocioException.class);

        Factura anulada = servicio.anular("f9", "Error de digitación");
        assertThat(anulada.getEstado()).isEqualTo(Factura.Estado.ANULADA);
        verify(pedidos).revertirPago("p1", "f9");
    }
}
