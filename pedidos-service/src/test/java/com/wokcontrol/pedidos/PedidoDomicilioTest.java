package com.wokcontrol.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.pedidos.client.InventarioClient;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.ConsumoInsumo;
import com.wokcontrol.pedidos.client.MenuClient.CotizacionResponse;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizado;
import com.wokcontrol.pedidos.dto.PedidoDtos.ItemRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoDomicilioRequest;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.repository.PedidoRepository;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;

/** v2 · Sprint 3 · CR-001: el pedido de un domicilio en pedidos-service (sin mesa, directo a cocina). */
class PedidoDomicilioTest {

    private PedidoRepository repo;
    private MesaService mesas;
    private InventarioClient inventario;
    private PedidoService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        mesas = mock(MesaService.class);
        MenuClient menu = mock(MenuClient.class);
        inventario = mock(InventarioClient.class);
        SecuenciaService secuencias = mock(SecuenciaService.class);
        when(secuencias.siguienteFormateado(anyString(), anyString())).thenReturn("P-000077");
        when(repo.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            when(repo.findById("p1")).thenReturn(Optional.of(p));
            return p;
        });
        when(repo.findFirstByDomicilioIdAndEstadoNot(anyString(), any())).thenReturn(Optional.empty());
        when(menu.cotizar(anyList())).thenReturn(new CotizacionResponse(List.of(new ItemCotizado("PLATO", "arroz",
                "Arroz frito", new BigDecimal("12500"), true, null, 1, Set.of(),
                List.of(new ConsumoInsumo("i-arroz", "Arroz", "KG", new BigDecimal("0.2"))))), true));
        servicio = new PedidoService(repo, mesas, menu, inventario, secuencias, mock(MongoTemplate.class),
                mock(AuditoriaPublisher.class), "America/Bogota");
        servicioInterno("caja1");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** Token de servicio con el que llama domicilios-service (actor = cajero o domiciliario). */
    private static void servicioInterno(String actor) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("domicilios-service").claim("tipo", "servicio")
                .claim("actor", actor).claim("permisos", List.of(Permisos.SISTEMA)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority(Permisos.SISTEMA))));
    }

    private static void usuario(String rol) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("u-" + rol).claim("nombre", "Usuario")
                .claim("roles", List.of(rol)).claim("permisos", List.copyOf(Roles.SISTEMA.get(rol).permisos())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                Roles.SISTEMA.get(rol).permisos().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private Pedido domicilio() {
        return servicio.crearDomicilio(new PedidoDomicilioRequest("dom1", "D-0001",
                List.of(new ItemRequest(ItemPedido.Tipo.PLATO, "arroz", 3, 1, null)), "Timbre dañado"));
    }

    private static void cocinaTermina(Pedido p) {
        p.getItems().forEach(i -> i.setEstado(ItemPedido.Estado.LISTO));
        p.recalcularEstado();
    }

    @Test
    void elDomicilioVaDirectoACocinaSinMesaYDescuentaInventario() {
        Pedido p = domicilio();

        assertThat(p.getEstado()).isEqualTo(EstadoPedido.ENVIADO);
        assertThat(p.getMesaId()).isNull();
        assertThat(p.esDomicilio()).isTrue();
        assertThat(p.getDestino()).isEqualTo("Domicilio D-0001");
        assertThat(p.getTotal()).isEqualByComparingTo("37500");
        assertThat(p.getMesero()).isEqualTo("caja1");
        verify(inventario).consumir(eq(p.referenciaInventario()), anyString(), anyList());
        verify(mesas, never()).actualizarOcupacion(anyString());
    }

    @Test
    void crearEsIdempotentePorDomicilio() {
        Pedido p = domicilio();
        when(repo.findFirstByDomicilioIdAndEstadoNot("dom1", EstadoPedido.CANCELADO)).thenReturn(Optional.of(p));
        assertThat(domicilio()).isSameAs(p);
    }

    @Test
    void soloSeEntregaCuandoCocinaTermina() {
        Pedido p = domicilio();
        servicioInterno("luis");
        assertThatThrownBy(() -> servicio.entregarDomicilio("p1"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> assertThat(ex.getCodigo()).isEqualTo("PEDIDO_NO_LISTO"));

        cocinaTermina(p);
        assertThat(servicio.entregarDomicilio("p1").getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        // repetir la llamada no cambia nada
        assertThat(servicio.entregarDomicilio("p1").getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    void elMeseroNoEntregaNiCancelaUnDomicilio() {
        Pedido p = domicilio();
        usuario(Roles.MESERO);
        assertThatThrownBy(() -> servicio.cancelar("p1", "El cliente ya no lo quiere")).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Domicilios");
        cocinaTermina(p);
        assertThatThrownBy(() -> servicio.cambiarEstadoItem("p1", p.getItems().get(0).getId(), ItemPedido.Estado.ENTREGADO))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(p.getEstado()).isEqualTo(EstadoPedido.LISTO);
    }

    @Test
    void ac052_1_noEntregadoAnulaSinFacturaYSinDevolverInventario() {
        Pedido p = domicilio();
        cocinaTermina(p);
        servicioInterno("luis");

        Pedido anulado = servicio.anularDomicilio("p1", "Domicilio D-0001 no entregado: el cliente no contesta");
        assertThat(anulado.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(anulado.getCanceladoPor()).isEqualTo("luis");
        verify(inventario, never()).revertir(anyString());
    }

    @Test
    void cancelarAntesDeCocinaRestituyeElInventario() {
        domicilio();
        Pedido cancelado = servicio.anularDomicilio("p1", "Domicilio D-0001 cancelado: el cliente llamó");
        assertThat(cancelado.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        verify(inventario).revertir(anyString());
    }
}
