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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.pedidos.client.InventarioClient;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.ConsumoInsumo;
import com.wokcontrol.pedidos.client.MenuClient.CotizacionResponse;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizado;
import com.wokcontrol.pedidos.client.MenuClient.ItemCotizar;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.repository.PedidoRepository;
import com.wokcontrol.pedidos.repository.SesionMesaRepository;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoClienteService;
import com.wokcontrol.pedidos.service.PedidoClienteService.Carrito;
import com.wokcontrol.pedidos.service.PedidoClienteService.CuentaSolicitada;
import com.wokcontrol.pedidos.service.PedidoService;

/**
 * Épica 7 · Pruebas escritas antes del código (constitución 2), una o más por criterio:
 * <ul>
 * <li>T005 · HU-032: AC-032.1, AC-032.2.</li>
 * <li>T007 · HU-034: AC-034.1, AC-034.2, AC-034.3.</li>
 * <li>T009 · HU-037: AC-037.1, AC-037.2 y RN-01.</li>
 * <li>T011 · HU-038: AC-038.1, AC-038.2.</li>
 * <li>T013 · HU-035: AC-035.1, AC-035.2.</li>
 * </ul>
 * Usa el {@link PedidoService} real con la base y los demás servicios simulados.
 */
class PedidoClienteServiceTest {

    private PedidoRepository repo;
    private MesaService mesas;
    private MenuClient menu;
    private InventarioClient inventario;
    private PedidoService pedidos;
    private PedidoClienteService cliente;
    private SesionMesaRepository sesionesRepo;
    private SesionMesa sesion;
    /** Carta simulada: id → [nombre, precio, disponible]. */
    private final Map<String, Object[]> carta = new HashMap<>();

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        mesas = mock(MesaService.class);
        menu = mock(MenuClient.class);
        inventario = mock(InventarioClient.class);
        SecuenciaService secuencias = mock(SecuenciaService.class);
        when(secuencias.siguienteFormateado(anyString(), anyString())).thenReturn("P-000123");
        when(repo.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            when(repo.findById(p.getId())).thenReturn(Optional.of(p));
            return p;
        });
        pedidos = new PedidoService(repo, mesas, menu, inventario, secuencias, mock(MongoTemplate.class),
                mock(AuditoriaPublisher.class), "America/Bogota");
        sesionesRepo = mock(SesionMesaRepository.class);
        when(sesionesRepo.save(any(SesionMesa.class))).thenAnswer(i -> i.getArgument(0));
        cliente = new PedidoClienteService(sesionesRepo, pedidos, repo, mesas, menu, mock(AuditoriaPublisher.class));

        Mesa mesa5 = new Mesa();
        mesa5.setId("m5");
        mesa5.setNumero(5);
        mesa5.setActiva(true);
        when(mesas.obtener("m5")).thenReturn(mesa5);

        sesion = new SesionMesa();
        sesion.setId("s1");
        sesion.setMesaId("m5");
        sesion.setMesaNumero(5);

        carta.put("arroz", new Object[] { "Arroz chino especial", new BigDecimal("28000"), true });
        carta.put("pato", new Object[] { "Pato laqueado", new BigDecimal("52000"), true });
        carta.put("lumpia", new Object[] { "Lumpias", new BigDecimal("12000"), true });
        when(menu.cotizar(anyList())).thenAnswer(i -> {
            List<ItemCotizar> pedidos = i.getArgument(0);
            List<ItemCotizado> r = pedidos.stream().map(x -> {
                Object[] d = carta.get(x.id());
                boolean disp = (boolean) d[2];
                return new ItemCotizado("PLATO", x.id(), (String) d[0], (BigDecimal) d[1], disp,
                        disp ? null : "Plato agotado", 0, Set.of(),
                        List.of(new ConsumoInsumo("i-" + x.id(), "Insumo", "KG", new BigDecimal("0.1"))));
            }).toList();
            return new CotizacionResponse(r, r.stream().allMatch(ItemCotizado::disponible));
        });
        autenticar(Roles.MESERO);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String rol) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("u-" + rol).claim("nombre", "Usuario " + rol)
                .claim("roles", List.of(rol)).claim("permisos", List.copyOf(Roles.SISTEMA.get(rol).permisos())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                Roles.SISTEMA.get(rol).permisos().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private Pedido pedidoQrEnviado() {
        cliente.agregar(sesion, "PLATO", "arroz", 2);
        cliente.agregar(sesion, "PLATO", "lumpia", 1);
        return cliente.enviar(sesion);
    }

    // ------------------------------------------------------------------ HU-032

    @Test
    void ac032_1_cambiarCantidadYObservacionRecalculaElTotal() {
        cliente.agregar(sesion, "PLATO", "arroz", 2);
        String linea = sesion.getCarrito().get(0).getId();

        cliente.cambiar(sesion, linea, 3, "sin cebolla");

        Carrito c = cliente.carrito(sesion);
        assertThat(c.lineas()).singleElement().satisfies(l -> {
            assertThat(l.cantidad()).isEqualTo(3);
            assertThat(l.notas()).isEqualTo("sin cebolla");
        });
        assertThat(c.total()).isEqualByComparingTo("84000");
    }

    @Test
    void hu032_agregarElMismoPlatoSumaYQuitarloLoSaca() {
        cliente.agregar(sesion, "PLATO", "arroz", 1);
        cliente.agregar(sesion, "PLATO", "arroz", 1);
        assertThat(sesion.getCarrito()).singleElement().satisfies(l -> assertThat(l.getCantidad()).isEqualTo(2));

        cliente.quitar(sesion, sesion.getCarrito().get(0).getId());

        assertThat(cliente.carrito(sesion).vacio()).isTrue();
    }

    @Test
    void ac032_2_unPedidoYaEnviadoNoSeCambiaDesdeElCelular() {
        Pedido p = pedidoQrEnviado();
        autenticar(Roles.MESERO);
        pedidos.confirmar(p.getId());

        assertThatThrownBy(() -> cliente.modificarEnviado(sesion, p.getId()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El pedido ya fue enviado; pide el cambio al mesero");
    }

    @Test
    void ac036_1_elClienteNoTocaPedidosDeOtraMesa() {
        Pedido ajeno = new Pedido();
        ajeno.setId("p7");
        ajeno.setMesaId("m7");
        when(repo.findById("p7")).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> cliente.modificarEnviado(sesion, "p7"))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    // ------------------------------------------------------------------ HU-034

    @Test
    void ac034_1_elPedidoEnviadoQuedaPorConfirmarYNoLlegaACocina() {
        Pedido p = pedidoQrEnviado();

        assertThat(p.getEstado()).isEqualTo(EstadoPedido.POR_CONFIRMAR);
        assertThat(p.getUnidades()).isEqualTo(3);
        assertThat(p.esQr()).isTrue();
        assertThat(p.getSesionMesaId()).isEqualTo("s1");
        assertThat(p.getTotal()).isEqualByComparingTo("68000");
        assertThat(sesion.getCarrito()).isEmpty();
        verify(inventario, never()).consumir(anyString(), anyString(), anyList());
        verify(mesas).actualizarOcupacion("m5");
        // el mesero lo ve en su bandeja de «Por confirmar»
        when(repo.findByEstadoInOrderByCreadoEnAsc(Set.of(EstadoPedido.POR_CONFIRMAR))).thenReturn(List.of(p));
        assertThat(pedidos.porConfirmar()).containsExactly(p);
        assertThat(EstadoPedido.EN_COCINA).doesNotContain(EstadoPedido.POR_CONFIRMAR);
    }

    @Test
    void ac034_2_elClienteVeElNuevoEstadoDeCadaPlato() {
        Pedido p = pedidoQrEnviado();
        assertThat(PedidoClienteService.estadoPlato(p, p.getItems().get(0))).isEqualTo("Por confirmar");
        pedidos.confirmar(p.getId());
        assertThat(PedidoClienteService.estadoPlato(p, p.getItems().get(0))).isEqualTo("En cocina");

        autenticar(Roles.COCINERO);
        String arroz = p.getItems().get(0).getId();
        pedidos.cambiarEstadoItem(p.getId(), arroz, ItemPedido.Estado.EN_PREPARACION);
        Pedido listo = pedidos.cambiarEstadoItem(p.getId(), arroz, ItemPedido.Estado.LISTO);

        assertThat(PedidoClienteService.estadoPlato(listo, listo.getItems().get(0))).isEqualTo("Listo");
        assertThat(PedidoClienteService.estadoPlato(listo, listo.getItems().get(1))).isEqualTo("En cocina");
    }

    @Test
    void ac034_otroCelularDeLaMismaMesaVeLosPedidosDeLaCuentaActual() {
        Pedido p = pedidoQrEnviado();
        SesionMesa otra = new SesionMesa();
        otra.setId("s2");
        otra.setMesaId("m5");
        when(sesionesRepo.findByMesaIdAndEstado("m5", SesionMesa.Estado.ACTIVA)).thenReturn(List.of(sesion, otra));
        when(repo.findBySesionMesaIdInOrderByCreadoEnAsc(Set.of("s1", "s2"))).thenReturn(List.of(p));
        Pedido ajeno = new Pedido();
        ajeno.setMesaId("m7");

        assertThat(cliente.pedidosDeSesion(otra)).containsExactly(p);
    }

    @Test
    void ac034_3_sinPlatosNoSeEnvia() {
        assertThatThrownBy(() -> cliente.enviar(sesion)).hasMessage("Agrega al menos un plato");
        verify(repo, never()).save(any(Pedido.class));
    }

    // ------------------------------------------------------------------ HU-037

    @Test
    void ac037_1_alConfirmarPasaACocinaComoComanda() {
        Pedido p = pedidoQrEnviado();

        Pedido confirmado = pedidos.confirmar(p.getId());

        assertThat(confirmado.getEstado()).isEqualTo(EstadoPedido.ENVIADO);
        assertThat(EstadoPedido.EN_COCINA).contains(confirmado.getEstado());
        assertThat(confirmado.getEnviadoEn()).isNotNull();
        assertThat(confirmado.getMesero()).isNotBlank();
        verify(inventario).consumir(eq("PEDIDO-p1"), anyString(), anyList());
    }

    @Test
    void ac037_2_elRechazoExigeMotivoYQuedaParaElCliente() {
        Pedido p = pedidoQrEnviado();

        assertThatThrownBy(() -> pedidos.rechazar(p.getId(), "  ")).hasMessage("Indica el motivo del rechazo");
        assertThat(p.getEstado()).isEqualTo(EstadoPedido.POR_CONFIRMAR);

        Pedido r = pedidos.rechazar(p.getId(), "La mesa está vacía");

        assertThat(r.getEstado()).isEqualTo(EstadoPedido.RECHAZADO);
        assertThat(r.getMotivoRechazo()).isEqualTo("La mesa está vacía");
        assertThat(EstadoPedido.ACTIVOS).doesNotContain(EstadoPedido.RECHAZADO);
        verify(inventario, never()).consumir(anyString(), anyString(), anyList());
    }

    @Test
    void rn01_unPedidoQrNoSeEnviaACocinaSinConfirmar() {
        Pedido p = pedidoQrEnviado();
        assertThatThrownBy(() -> pedidos.enviarACocina(p.getId())).isInstanceOf(ReglaNegocioException.class);
    }

    // ------------------------------------------------------------------ HU-038

    @Test
    void ac038_1_siSeAgotaUnPlatoDelPedidoSinEnviarSeMarcaYSeAvisa() {
        cliente.agregar(sesion, "PLATO", "pato", 1);
        cliente.agregar(sesion, "PLATO", "arroz", 1);
        String antes = cliente.huella(sesion);

        carta.get("pato")[2] = false; // el cocinero lo marca agotado en menu-service

        Carrito c = cliente.carrito(sesion);
        assertThat(c.hayAgotados()).isTrue();
        assertThat(c.lineas().get(0).agotado()).isTrue();
        assertThat(c.lineas().get(1).agotado()).isFalse();
        assertThat(c.total()).isEqualByComparingTo("28000");
        assertThat(cliente.huella(sesion)).as("el celular detecta el cambio y recarga").isNotEqualTo(antes);
    }

    @Test
    void ac038_2_conPlatosAgotadosNoSeEnvia() {
        cliente.agregar(sesion, "PLATO", "pato", 1);
        carta.get("pato")[2] = false;

        assertThatThrownBy(() -> cliente.enviar(sesion)).hasMessage("Quita los platos agotados antes de enviar");
        verify(repo, never()).save(any(Pedido.class));
    }

    @Test
    void hu038_noSeAgregaUnPlatoAgotado() {
        carta.get("pato")[2] = false;
        assertThatThrownBy(() -> cliente.agregar(sesion, "PLATO", "pato", 1)).hasMessageContaining("agotado");
        assertThat(sesion.getCarrito()).isEmpty();
    }

    // ------------------------------------------------------------------ HU-035

    @Test
    void ac035_1_conTodoEntregadoLaMesaQuedaComoCuentaSolicitadaConElTotal() {
        Pedido p = pedidoQrEnviado();
        p.getItems().forEach(i -> i.setEstado(ItemPedido.Estado.ENTREGADO));
        p.setEstado(EstadoPedido.ENTREGADO);
        when(repo.findByMesaIdAndEstadoInOrderByCreadoEnAsc("m5", EstadoPedido.ACTIVOS)).thenReturn(List.of(p));

        CuentaSolicitada c = cliente.pedirCuenta(sesion);

        assertThat(c.total()).isEqualByComparingTo("68000");
        assertThat(c.platosSinEntregar()).isZero();
        verify(mesas).solicitarCuenta("m5");
    }

    @Test
    void ac035_2_conPlatosEnPreparacionSeRegistraYAvisaAlCajero() {
        Pedido p = pedidoQrEnviado();
        pedidos.confirmar(p.getId());
        when(repo.findByMesaIdAndEstadoInOrderByCreadoEnAsc("m5", EstadoPedido.ACTIVOS)).thenReturn(List.of(p));

        CuentaSolicitada c = cliente.pedirCuenta(sesion);

        assertThat(c.platosSinEntregar()).isEqualTo(3);
        assertThat(MesaService.platosSinEntregar(p)).isEqualTo(3);
        verify(mesas).solicitarCuenta("m5");
    }

    @Test
    void hu035_sinPedidosNoSePideLaCuenta() {
        assertThatThrownBy(() -> cliente.pedirCuenta(sesion)).isInstanceOf(ReglaNegocioException.class);
        verify(mesas, never()).solicitarCuenta(anyString());
    }
}
