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
import org.mockito.ArgumentCaptor;
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
import com.wokcontrol.pedidos.client.InventarioClient;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.ConsumoInsumo;
import com.wokcontrol.pedidos.client.MenuClient.CotizacionResponse;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizado;
import com.wokcontrol.pedidos.dto.PedidoDtos.ItemRequest;
import com.wokcontrol.pedidos.dto.PedidoDtos.PedidoRequest;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.repository.PedidoRepository;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;

class PedidoServiceTest {

    private PedidoRepository repo;
    private MesaService mesas;
    private MenuClient menu;
    private InventarioClient inventario;
    private PedidoService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        mesas = mock(MesaService.class);
        menu = mock(MenuClient.class);
        inventario = mock(InventarioClient.class);
        SecuenciaService secuencias = mock(SecuenciaService.class);
        when(secuencias.siguienteFormateado(anyString(), anyString())).thenReturn("P-000001");
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new PedidoService(repo, mesas, menu, inventario, secuencias, mock(MongoTemplate.class),
                mock(AuditoriaPublisher.class), "America/Bogota");
        Mesa mesa = new Mesa();
        mesa.setId("m1");
        mesa.setNumero(4);
        mesa.setActiva(true);
        when(mesas.obtener("m1")).thenReturn(mesa);
        autenticar(Roles.MESERO);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String rol) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("u-" + rol).claim("nombre", "Usuario")
                .claim("roles", List.of(rol)).claim("permisos", List.copyOf(Roles.SISTEMA.get(rol).permisos())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                Roles.SISTEMA.get(rol).permisos().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private void carta(boolean disponible) {
        when(menu.cotizar(anyList())).thenReturn(new CotizacionResponse(List.of(new ItemCotizado("PLATO", "arroz",
                "Arroz frito", new BigDecimal("25000"), disponible, disponible ? null : "Plato no disponible", 1,
                Set.of("SOYA"), List.of(new ConsumoInsumo("i-arroz", "Arroz", "KG", new BigDecimal("0.2"))))), disponible));
    }

    private Pedido pedidoCreado() {
        carta(true);
        return servicio.crear(new PedidoRequest("m1",
                List.of(new ItemRequest(ItemPedido.Tipo.PLATO, "arroz", 3, 2, "sin cebolla")), "Cumpleaños", Set.of("MANI")));
    }

    private Pedido guardado(Pedido p) {
        p.setId("p1");
        when(repo.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void creaPedidoConPrecioCopiadoDeLaCartaYMesaOcupada() {
        Pedido p = pedidoCreado();

        assertThat(p.getEstado()).isEqualTo(EstadoPedido.ABIERTO);
        assertThat(p.getItems()).singleElement().satisfies(i -> {
            assertThat(i.getPrecioUnitario()).isEqualByComparingTo("25000");
            assertThat(i.getNivelPicante()).isEqualTo(2);
            assertThat(i.getAlergenos()).containsExactly("SOYA");
        });
        assertThat(p.getTotal()).isEqualByComparingTo("75000");
        assertThat(p.getAlergias()).containsExactly("MANI");
        verify(mesas).actualizarOcupacion("m1");
    }

    @Test
    void rechazaProductosNoDisponibles() {
        carta(false);
        assertThatThrownBy(() -> servicio.crear(new PedidoRequest("m1",
                List.of(new ItemRequest(ItemPedido.Tipo.PLATO, "arroz", 1, 0, null)), null, null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no disponibles");
    }

    @Test
    void enviarACocinaDescuentaInventarioConReferenciaIdempotente() {
        Pedido p = guardado(pedidoCreado());

        Pedido enviado = servicio.enviarACocina("p1");

        assertThat(enviado.getEstado()).isEqualTo(EstadoPedido.ENVIADO);
        assertThat(enviado.getConsumoInventario()).isEqualTo(Pedido.EstadoConsumo.APLICADO);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventarioClient.LineaConsumo>> lineas = ArgumentCaptor.forClass(List.class);
        verify(inventario).consumir(eq("PEDIDO-p1"), anyString(), lineas.capture());
        assertThat(lineas.getValue()).singleElement().satisfies(l -> {
            assertThat(l.insumoId()).isEqualTo("i-arroz");
            assertThat(l.cantidad()).isEqualByComparingTo("0.6");
        });
        assertThatThrownBy(() -> servicio.actualizar("p1", null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya fue enviado");
        assertThat(p).isSameAs(enviado);
    }

    @Test
    void siInventarioFallaElPedidoSigueAbierto() {
        guardado(pedidoCreado());
        when(inventario.consumir(anyString(), anyString(), anyList()))
                .thenThrow(new ServicioRemotoException("inventario-service", "no disponible", null));

        assertThatThrownBy(() -> servicio.enviarACocina("p1")).isInstanceOf(ServicioRemotoException.class);
        assertThat(servicio.obtener("p1").getEstado()).isEqualTo(EstadoPedido.ABIERTO);
        verify(repo, never()).save(org.mockito.ArgumentMatchers.argThat(x -> x.getEstado() == EstadoPedido.ENVIADO));
    }

    @Test
    void cancelarEnCocinaSinPrepararRevierteInventarioYRegistraMotivo() {
        guardado(pedidoCreado());
        servicio.enviarACocina("p1");

        Pedido c = servicio.cancelar("p1", "El cliente se retiró");

        assertThat(c.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(c.getConsumoInventario()).isEqualTo(Pedido.EstadoConsumo.REVERTIDO);
        assertThat(c.getCanceladoPor()).isEqualTo("u-MESERO");
        assertThat(c.getMotivoCancelacion()).isEqualTo("El cliente se retiró");
        verify(inventario).revertir("PEDIDO-p1");
    }

    @Test
    void cancelarExigeMotivoYNoSePermiteSiCocinaYaEmpezo() {
        Pedido p = guardado(pedidoCreado());
        assertThatThrownBy(() -> servicio.cancelar("p1", "")).isInstanceOf(ReglaNegocioException.class);
        servicio.enviarACocina("p1");
        autenticar(Roles.COCINERO);
        servicio.cambiarEstadoItem("p1", p.getItems().get(0).getId(), ItemPedido.Estado.EN_PREPARACION);
        autenticar(Roles.MESERO);

        assertThatThrownBy(() -> servicio.cancelar("p1", "Ya no lo quiere")).isInstanceOf(ReglaNegocioException.class);
        verify(inventario, never()).revertir(anyString());
    }

    @Test
    void flujoCompletoDeEstadosHastaPagado() {
        Pedido p = guardado(pedidoCreado());
        String item = p.getItems().get(0).getId();
        servicio.enviarACocina("p1");

        autenticar(Roles.COCINERO);
        assertThat(servicio.cambiarEstadoItem("p1", item, ItemPedido.Estado.EN_PREPARACION).getEstado())
                .isEqualTo(EstadoPedido.EN_PREPARACION);
        assertThatThrownBy(() -> servicio.cambiarEstadoItem("p1", item, ItemPedido.Estado.ENTREGADO))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThat(servicio.cambiarEstadoItem("p1", item, ItemPedido.Estado.LISTO).getEstado()).isEqualTo(EstadoPedido.LISTO);

        autenticar(Roles.MESERO);
        assertThatThrownBy(() -> servicio.marcarPagado("p1", "F1")).isInstanceOf(ReglaNegocioException.class);
        assertThat(servicio.cambiarEstadoItem("p1", item, ItemPedido.Estado.ENTREGADO).getEstado()).isEqualTo(EstadoPedido.ENTREGADO);

        assertThat(servicio.marcarPagado("p1", "F1").getEstado()).isEqualTo(EstadoPedido.PAGADO);
        // reintento con la misma factura: idempotente
        assertThat(servicio.marcarPagado("p1", "F1").getEstado()).isEqualTo(EstadoPedido.PAGADO);
        assertThatThrownBy(() -> servicio.marcarPagado("p1", "F2")).isInstanceOf(ConflictoException.class);
    }

    @Test
    void meseroNoPuedeCambiarEstadosDePreparacionYNoSeSaltanEstados() {
        Pedido p = guardado(pedidoCreado());
        String item = p.getItems().get(0).getId();
        servicio.enviarACocina("p1");
        assertThatThrownBy(() -> servicio.cambiarEstadoItem("p1", item, ItemPedido.Estado.EN_PREPARACION))
                .isInstanceOf(OperacionNoPermitidaException.class);
        autenticar(Roles.COCINERO);
        assertThatThrownBy(() -> servicio.cambiarEstadoItem("p1", item, ItemPedido.Estado.LISTO))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Transición inválida");
    }
}
