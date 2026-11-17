package com.wokcontrol.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.pedidos.client.InventarioClient;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.UsuariosClient;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignacionResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignarRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.GrupoRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.MapaSalon;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.GrupoEspera;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.repository.GrupoEsperaRepository;
import com.wokcontrol.pedidos.repository.MesaRepository;
import com.wokcontrol.pedidos.repository.PedidoRepository;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;
import com.wokcontrol.pedidos.service.SalonService;
import com.wokcontrol.pedidos.service.SesionMesaService;

/**
 * Épica 8 · Pruebas escritas antes del código (constitución 2), una o más por criterio:
 * <ul>
 * <li>T003 · HU-040: AC-040.1, AC-040.2.</li>
 * <li>T005 · HU-041: AC-041.1, AC-041.2.</li>
 * <li>T007 · HU-042: AC-042.1, AC-042.2 y RN-07 (pedido QR al mesero asignado).</li>
 * <li>T009 · HU-044: AC-044.1, AC-044.2.</li>
 * <li>T011 · HU-045: AC-045.1, AC-045.2.</li>
 * <li>T013 · HU-046: AC-046.1 (la huella cambia cuando cambia una mesa).</li>
 * </ul>
 * Usa {@link SalonService}, {@link MesaService} y {@link PedidoService} reales con la base simulada en memoria.
 */
class SalonServiceTest {

    private final Map<String, Mesa> mesasDb = new LinkedHashMap<>();
    private final Map<String, GrupoEspera> esperaDb = new LinkedHashMap<>();
    private final Map<String, Integer> pedidosActivos = new HashMap<>();
    private MesaRepository mesasRepo;
    private PedidoRepository pedidosRepo;
    private SesionMesaService sesiones;
    private UsuariosClient usuarios;
    private AuditoriaPublisher auditoria;
    private MesaService mesas;
    private SalonService salon;

    @BeforeEach
    void setUp() {
        mesasRepo = mock(MesaRepository.class);
        when(mesasRepo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(mesasDb.get(i.<String>getArgument(0))));
        when(mesasRepo.save(any(Mesa.class))).thenAnswer(i -> {
            Mesa m = i.getArgument(0);
            m.setVersion(m.getVersion() == null ? 1L : m.getVersion() + 1);
            mesasDb.put(m.getId(), m);
            return m;
        });
        when(mesasRepo.findByActivaTrueOrderByNumeroAsc()).thenAnswer(i -> mesasDb.values().stream().filter(Mesa::isActiva)
                .sorted(Comparator.comparingInt(Mesa::getNumero)).toList());
        when(mesasRepo.findAllByOrderByNumeroAsc()).thenAnswer(i -> mesasDb.values().stream()
                .sorted(Comparator.comparingInt(Mesa::getNumero)).toList());
        when(mesasRepo.findByAsignacionUnionId(anyString())).thenAnswer(i -> mesasDb.values().stream()
                .filter(m -> m.getAsignacion() != null && i.getArgument(0).equals(m.getAsignacion().unionId()))
                .sorted(Comparator.comparingInt(Mesa::getNumero)).toList());

        pedidosRepo = mock(PedidoRepository.class);
        when(pedidosRepo.countByMesaIdAndEstadoIn(anyString(), any()))
                .thenAnswer(i -> (long) pedidosActivos.getOrDefault(i.<String>getArgument(0), 0));
        when(pedidosRepo.findByEstadoInOrderByCreadoEnAsc(any())).thenReturn(List.of());

        GrupoEsperaRepository esperaRepo = mock(GrupoEsperaRepository.class);
        when(esperaRepo.save(any(GrupoEspera.class))).thenAnswer(i -> {
            GrupoEspera g = i.getArgument(0);
            if (g.getId() == null) {
                g.setId(new ObjectId().toHexString());
            }
            g.setVersion(g.getVersion() == null ? 0L : g.getVersion() + 1);
            esperaDb.put(g.getId(), g);
            return g;
        });
        when(esperaRepo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(esperaDb.get(i.<String>getArgument(0))));
        when(esperaRepo.findByEstadoOrderByLlegadaEnAsc(any())).thenAnswer(i -> esperaDb.values().stream()
                .filter(g -> g.getEstado() == i.getArgument(0)).sorted(Comparator.comparing(GrupoEspera::getLlegadaEn)).toList());

        sesiones = mock(SesionMesaService.class);
        auditoria = mock(AuditoriaPublisher.class);
        usuarios = mock(UsuariosClient.class);
        when(usuarios.meseros()).thenReturn(List.of(new UsuariosClient.Mesero("carlos", "Carlos Pérez"),
                new UsuariosClient.Mesero("ana", "Ana Gómez")));

        mesas = new MesaService(mesasRepo, pedidosRepo, mock(MongoTemplate.class), auditoria, sesiones);
        salon = new SalonService(esperaRepo, mesasRepo, mesas, usuarios, auditoria, "America/Bogota");
    }

    private Mesa mesa(int numero, int capacidad, Mesa.Estado estado) {
        Mesa m = new Mesa();
        m.setId("m" + numero);
        m.setNumero(numero);
        m.setCapacidad(capacidad);
        m.setZona("SALON");
        m.setActiva(true);
        m.setEstado(estado);
        m.setVersion(0L);
        mesasDb.put(m.getId(), m);
        return m;
    }

    private GrupoEspera enLista(String nombre, int personas) {
        return salon.registrar(new GrupoRequest(nombre, personas, null, null));
    }

    // ------------------------------------------------------------------ HU-040

    @Test
    void ac040_1_elGrupoQuedaAlFinalDeLaListaConSuHoraYTiempoDeEspera() {
        for (int i = 1; i <= 4; i++) {
            mesa(i, 4, Mesa.Estado.OCUPADA); // el salón lleno
        }
        ZoneId zona = ZoneId.of("America/Bogota");
        String antes = LocalTime.now(zona).minusMinutes(20).format(DateTimeFormatter.ofPattern("HH:mm"));
        salon.registrar(new GrupoRequest("Pareja Díaz", 2, antes, null));
        GrupoEspera rojas = salon.registrar(new GrupoRequest("Familia Rojas", 4, null, "Silla para bebé"));

        List<GrupoEspera> lista = salon.espera();
        assertThat(lista).extracting(GrupoEspera::getNombre).containsExactly("Pareja Díaz", "Familia Rojas");
        assertThat(rojas.getLlegadaEn()).isBetween(Instant.now().minusSeconds(5), Instant.now());
        assertThat(rojas.getPersonas()).isEqualTo(4);
        assertThat(lista.get(0).minutosEspera(Instant.now())).isBetween(19L, 21L);
        assertThat(salon.esperaRespuesta().get(1).minutosEspera()).isZero();
        verify(auditoria).registrar(eq("REGISTRAR_ESPERA"), eq("Grupo"), eq(rojas.getId()), anyString());
    }

    @Test
    void ac040_2_sinNombreOConCeroPersonasSeRechazaIndicandoElCampo() {
        assertThatThrownBy(() -> salon.registrar(new GrupoRequest("  ", 4, null, null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageStartingWith("nombre:");
        assertThatThrownBy(() -> salon.registrar(new GrupoRequest("Familia Rojas", 0, null, null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageStartingWith("personas:");
        assertThatThrownBy(() -> salon.registrar(new GrupoRequest("Familia Rojas", null, null, null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageStartingWith("personas:");
        assertThat(salon.espera()).isEmpty();
    }

    @Test
    void hu040_seConsultaEditaYRetiraUnGrupoDeLaLista() {
        GrupoEspera g = enLista("Familia Rojas", 4);
        salon.editar(g.getId(), new GrupoRequest("Familia Rojas Gil", 5, null, "Terraza"));
        assertThat(salon.grupo(g.getId())).satisfies(x -> {
            assertThat(x.getNombre()).isEqualTo("Familia Rojas Gil");
            assertThat(x.getPersonas()).isEqualTo(5);
            assertThat(x.getNotas()).isEqualTo("Terraza");
        });

        salon.retirar(g.getId(), "Se cansó de esperar");
        assertThat(salon.espera()).isEmpty();
        assertThat(salon.grupo(g.getId()).getEstado()).isEqualTo(GrupoEspera.Estado.RETIRADO);
        assertThatThrownBy(() -> salon.editar(g.getId(), new GrupoRequest("X", 2, null, null)))
                .hasMessageContaining("ya no está en la lista");
    }

    @Test
    void hu040_laHoraDeLlegadaNoPuedeSerFutura() {
        String futura = LocalTime.now(ZoneId.of("America/Bogota")).plusMinutes(30).format(DateTimeFormatter.ofPattern("HH:mm"));
        // cerca de la medianoche la hora +30 min es de madrugada y por tanto pasada: solo se prueba si es futura hoy
        if (LocalTime.now(ZoneId.of("America/Bogota")).isBefore(LocalTime.of(23, 0))) {
            assertThatThrownBy(() -> salon.registrar(new GrupoRequest("Familia Rojas", 4, futura, null)))
                    .hasMessageStartingWith("llegada:");
        }
    }

    // ------------------------------------------------------------------ HU-041

    @Test
    void ac041_1_elMapaMuestraCadaMesaConEstadoCapacidadYTotales() {
        for (int i = 1; i <= 5; i++) {
            mesa(i, 4, Mesa.Estado.DISPONIBLE);
        }
        for (int i = 6; i <= 11; i++) {
            mesa(i, 2 + i % 3, Mesa.Estado.OCUPADA);
        }
        mesa(12, 6, Mesa.Estado.POR_LIMPIAR);

        MapaSalon mapa = salon.mapa();

        assertThat(mapa.mesas()).hasSize(12);
        assertThat(mapa.libres()).isEqualTo(5);
        assertThat(mapa.ocupadas()).isEqualTo(6);
        assertThat(mapa.porLimpiar()).isEqualTo(1);
        assertThat(mapa.mesas().get(11)).satisfies(m -> {
            assertThat(m.numero()).isEqualTo(12);
            assertThat(m.capacidad()).isEqualTo(6);
            assertThat(m.estadoEtiqueta()).isEqualTo("Por limpiar");
        });
        assertThat(mapa.mesas().get(0).estadoEtiqueta()).isEqualTo("Libre");
    }

    @Test
    void ac041_2_unaMesaInactivaNoApareceEnElMapa() {
        mesa(1, 4, Mesa.Estado.DISPONIBLE);
        mesa(2, 4, Mesa.Estado.DISPONIBLE).setActiva(false);

        MapaSalon mapa = salon.mapa();

        assertThat(mapa.mesas()).extracting(m -> m.numero()).containsExactly(1);
        assertThat(mapa.libres()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ HU-042

    @Test
    void ac042_1_laMesaPasaAOcupadaElGrupoSaleDeLaListaYCarlosLaVeEnSusMesas() {
        mesa(3, 4, Mesa.Estado.DISPONIBLE);
        GrupoEspera g = enLista("Familia Rojas", 4);

        AsignacionResponse r = salon.asignar(new AsignarRequest(g.getId(), null, null, List.of("m3"), "carlos"));

        Mesa m3 = mesasDb.get("m3");
        assertThat(m3.getEstado()).isEqualTo(Mesa.Estado.OCUPADA);
        assertThat(m3.getAsignacion().mesero()).isEqualTo("carlos");
        assertThat(m3.getAsignacion().grupo()).isEqualTo("Familia Rojas");
        assertThat(salon.espera()).isEmpty();
        assertThat(salon.grupo(g.getId()).getEstado()).isEqualTo(GrupoEspera.Estado.SENTADO);
        assertThat(r.grupo().meseroNombre()).isEqualTo("Carlos Pérez");
        assertThat(r.union()).isFalse();
        // «sus mesas»: el tablero filtra por meseroAsignado (MesaWebController ?mias=true)
        assertThat(mesas.tablero(false)).filteredOn(x -> "carlos".equals(x.meseroAsignado()))
                .extracting(x -> x.numero()).containsExactly(3);
        verify(auditoria).registrar(eq("SENTAR_GRUPO"), eq("Mesa"), eq("m3"), anyString());
    }

    @Test
    void ac042_2_unGrupoDe6EnLaMesaDe4SeRechazaConCapacidadInsuficiente() {
        mesa(3, 4, Mesa.Estado.DISPONIBLE);
        GrupoEspera g = enLista("Familia Rojas", 6);

        assertThatThrownBy(() -> salon.asignar(new AsignarRequest(g.getId(), null, null, List.of("m3"), "carlos")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo(SalonService.CAPACIDAD_INSUFICIENTE))
                .hasMessageContaining("CAPACIDAD_INSUFICIENTE").hasMessageContaining("4 puestos");
        assertThat(mesasDb.get("m3").getEstado()).isEqualTo(Mesa.Estado.DISPONIBLE);
        assertThat(salon.espera()).hasSize(1);
    }

    @Test
    void hu042_elMeseroDebeEstarActivoYUnGrupoQueLlegaSePuedeSentarDirecto() {
        mesa(3, 4, Mesa.Estado.DISPONIBLE);
        assertThatThrownBy(() -> salon.asignar(new AsignarRequest(null, "Pareja Díaz", 2, List.of("m3"), "pedro")))
                .hasMessageContaining("no está activo");
        assertThatThrownBy(() -> salon.asignar(new AsignarRequest(null, "Pareja Díaz", 2, List.of("m3"), " ")))
                .hasMessageContaining("Elige el mesero");

        AsignacionResponse r = salon.asignar(new AsignarRequest(null, "Pareja Díaz", 2, List.of("m3"), "ana"));

        assertThat(r.grupo().estado()).isEqualTo(GrupoEspera.Estado.SENTADO);
        assertThat(mesasDb.get("m3").getAsignacion().mesero()).isEqualTo("ana");
    }

    @Test
    void rn07_elPedidoQrVaAlMeseroAsignadoALaMesa() {
        Mesa m3 = mesa(3, 4, Mesa.Estado.DISPONIBLE);
        GrupoEspera g = enLista("Familia Rojas", 4);
        salon.asignar(new AsignarRequest(g.getId(), null, null, List.of("m3"), "carlos"));

        MenuClient menu = mock(MenuClient.class);
        when(menu.cotizar(any())).thenReturn(new MenuClient.CotizacionResponse(List.of(new MenuClient.ItemCotizado("PLATO",
                "arroz", "Arroz", new java.math.BigDecimal("28000"), true, null, 0, Set.of(), List.of())), true));
        SecuenciaService secuencias = mock(SecuenciaService.class);
        when(secuencias.siguienteFormateado(anyString(), anyString())).thenReturn("P-000900");
        when(pedidosRepo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        MesaService mesasMock = mock(MesaService.class);
        when(mesasMock.obtener("m3")).thenReturn(m3);
        PedidoService pedidos = new PedidoService(pedidosRepo, mesasMock, menu, mock(InventarioClient.class), secuencias,
                mock(MongoTemplate.class), auditoria, "America/Bogota");

        Pedido p = pedidos.crearDesdeQr("m3", "s1",
                List.of(new com.wokcontrol.pedidos.dto.PedidoDtos.ItemRequest(ItemPedido.Tipo.PLATO, "arroz", 1, 0, null)));

        assertThat(p.getEstado()).isEqualTo(EstadoPedido.POR_CONFIRMAR);
        assertThat(p.getMesero()).isEqualTo("carlos");
        assertThat(p.getMeseroNombre()).isEqualTo("Carlos Pérez");
    }

    // ------------------------------------------------------------------ HU-044

    @Test
    void ac044_1_alCobrarLaCuentaLaMesaPasaAPorLimpiar() {
        mesa(3, 4, Mesa.Estado.DISPONIBLE);
        salon.asignar(new AsignarRequest(null, "Familia Rojas", 4, List.of("m3"), "carlos"));
        pedidosActivos.put("m3", 0); // el cajero cobró el único pedido

        mesas.alCobrar("m3");

        Mesa m3 = mesasDb.get("m3");
        assertThat(m3.getEstado()).isEqualTo(Mesa.Estado.POR_LIMPIAR);
        assertThat(m3.getAsignacion()).isNull();
        assertThat(m3.getPorLimpiarDesde()).isNotNull();
        verify(sesiones).cerrarDeMesa("m3", "Cuenta cobrada");
        verify(auditoria).registrar(eq("MESA_POR_LIMPIAR"), eq("Mesa"), eq("m3"), anyString());
    }

    @Test
    void ac044_1_siQuedanPedidosSinCobrarLaMesaSigueOcupada() {
        mesa(3, 4, Mesa.Estado.OCUPADA);
        pedidosActivos.put("m3", 1);

        mesas.alCobrar("m3");

        assertThat(mesasDb.get("m3").getEstado()).isEqualTo(Mesa.Estado.OCUPADA);
    }

    @Test
    void ac044_2_unaMesaPorLimpiarNoSeAsignaYAlMarcarlaLimpiaQuedaLibre() {
        mesa(3, 4, Mesa.Estado.POR_LIMPIAR);
        GrupoEspera g = enLista("Familia Rojas", 4);

        assertThatThrownBy(() -> salon.asignar(new AsignarRequest(g.getId(), null, null, List.of("m3"), "carlos")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo(SalonService.MESA_NO_DISPONIBLE))
                .hasMessageContaining("por limpiar");

        mesas.marcarLimpia("m3");

        assertThat(mesasDb.get("m3").getEstado()).isEqualTo(Mesa.Estado.DISPONIBLE);
        assertThat(salon.mapa().mesas().get(0).estadoEtiqueta()).isEqualTo("Libre");
        salon.asignar(new AsignarRequest(g.getId(), null, null, List.of("m3"), "carlos"));
        assertThat(mesasDb.get("m3").getEstado()).isEqualTo(Mesa.Estado.OCUPADA);
    }

    @Test
    void hu044_soloSeMarcaLimpiaUnaMesaPorLimpiarYElGrupoQueSeVaLaDejaPorLimpiar() {
        mesa(3, 4, Mesa.Estado.DISPONIBLE);
        assertThatThrownBy(() -> mesas.marcarLimpia("m3")).hasMessageContaining("no está por limpiar");

        salon.asignar(new AsignarRequest(null, "Familia Rojas", 4, List.of("m3"), "carlos"));
        mesas.liberar("m3"); // se fueron sin pedir

        assertThat(mesasDb.get("m3").getEstado()).isEqualTo(Mesa.Estado.POR_LIMPIAR);
        verify(sesiones).cerrarDeMesa("m3", "Mesa liberada");
    }

    // ------------------------------------------------------------------ HU-045

    @Test
    void ac045_1_lasMesas4y5SeUnenEnUnGrupoDe8PuestosOcupado() {
        mesa(4, 4, Mesa.Estado.DISPONIBLE);
        mesa(5, 4, Mesa.Estado.DISPONIBLE);
        GrupoEspera g = enLista("Cumpleaños Pérez", 8);

        AsignacionResponse r = salon.asignar(new AsignarRequest(g.getId(), null, null, List.of("m5", "m4"), "carlos"));

        assertThat(r.union()).isTrue();
        assertThat(r.puestos()).isEqualTo(8);
        assertThat(r.grupo().mesaNumeros()).containsExactly(4, 5);
        Mesa m4 = mesasDb.get("m4");
        Mesa m5 = mesasDb.get("m5");
        assertThat(m4.getEstado()).isEqualTo(Mesa.Estado.OCUPADA);
        assertThat(m5.getEstado()).isEqualTo(Mesa.Estado.OCUPADA);
        assertThat(m4.getAsignacion().unionId()).isNotNull().isEqualTo(m5.getAsignacion().unionId());
        assertThat(m4.getAsignacion().unionNumeros()).containsExactly(4, 5);
        verify(auditoria).registrar(eq("UNIR_MESAS"), eq("Mesa"), eq("m4"), anyString());
    }

    @Test
    void ac045_1_laUnionSeSeparaAlLiberarse() {
        mesa(4, 4, Mesa.Estado.DISPONIBLE);
        mesa(5, 4, Mesa.Estado.DISPONIBLE);
        salon.asignar(new AsignarRequest(null, "Cumpleaños Pérez", 8, List.of("m4", "m5"), "carlos"));
        pedidosActivos.put("m4", 1); // el pedido se registró en la mesa 4

        mesas.alCobrar("m5"); // con un pedido abierto en la unión, nada cambia
        assertThat(mesasDb.get("m5").getEstado()).isEqualTo(Mesa.Estado.OCUPADA);

        pedidosActivos.put("m4", 0);
        mesas.alCobrar("m4");

        assertThat(List.of(mesasDb.get("m4"), mesasDb.get("m5"))).allSatisfy(m -> {
            assertThat(m.getEstado()).isEqualTo(Mesa.Estado.POR_LIMPIAR);
            assertThat(m.getAsignacion()).isNull();
        });
        mesas.marcarLimpia("m4");
        assertThat(mesasDb.get("m4").getEstado()).isEqualTo(Mesa.Estado.DISPONIBLE);
        assertThat(mesasDb.get("m5").getEstado()).isEqualTo(Mesa.Estado.POR_LIMPIAR);
    }

    @Test
    void ac045_2_unaMesaLibreYOtraOcupadaNoSePuedenUnir() {
        mesa(4, 4, Mesa.Estado.DISPONIBLE);
        mesa(5, 4, Mesa.Estado.OCUPADA);

        assertThatThrownBy(() -> salon.asignar(new AsignarRequest(null, "Cumpleaños Pérez", 8, List.of("m4", "m5"), "carlos")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo(SalonService.MESA_NO_DISPONIBLE))
                .hasMessageContaining("La mesa 5 está ocupada");
        assertThat(mesasDb.get("m4").getEstado()).isEqualTo(Mesa.Estado.DISPONIBLE);
        assertThat(mesasDb.get("m4").getAsignacion()).isNull();
    }

    @Test
    void adr003_siOtraTabletOcupaUnaMesaDeLaUnionSeDeshaceTodo() {
        mesa(4, 4, Mesa.Estado.DISPONIBLE);
        mesa(5, 4, Mesa.Estado.DISPONIBLE);
        when(mesasRepo.save(any(Mesa.class))).thenAnswer(i -> {
            Mesa m = i.getArgument(0);
            if (m.getId().equals("m5") && m.getEstado() == Mesa.Estado.OCUPADA) {
                throw new OptimisticLockingFailureException("versión");
            }
            mesasDb.put(m.getId(), m);
            return m;
        });

        assertThatThrownBy(() -> salon.asignar(new AsignarRequest(null, "Cumpleaños Pérez", 8, List.of("m4", "m5"), "carlos")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo(SalonService.MESA_NO_DISPONIBLE));
        assertThat(mesasDb.get("m4").getEstado()).isEqualTo(Mesa.Estado.DISPONIBLE);
        assertThat(mesasDb.get("m4").getAsignacion()).isNull();
    }

    // ------------------------------------------------------------------ HU-046

    @Test
    void ac046_1_laHuellaCambiaCuandoUnaTabletCambiaLaMesa3YNoCambiaSinCambios() {
        mesa(3, 4, Mesa.Estado.DISPONIBLE);
        mesa(4, 4, Mesa.Estado.DISPONIBLE);
        String antes = salon.huella();
        assertThat(salon.huella()).isEqualTo(antes).hasSize(16);

        salon.asignar(new AsignarRequest(null, "Familia Rojas", 4, List.of("m3"), "carlos"));
        String despues = salon.huella();
        assertThat(despues).isNotEqualTo(antes);

        enLista("Pareja Díaz", 2);
        assertThat(salon.huella()).isNotEqualTo(despues);
    }

    @Test
    void hu046_elTiempoDeEsperaSeCalculaDesdeLaLlegada() {
        GrupoEspera g = new GrupoEspera();
        g.setLlegadaEn(Instant.now().minus(Duration.ofMinutes(12)));
        assertThat(g.minutosEspera(Instant.now())).isEqualTo(12);
    }
}
