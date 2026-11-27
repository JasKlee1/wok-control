package com.wokcontrol.domicilios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.domicilios.client.FacturacionClient;
import com.wokcontrol.domicilios.client.ParametrosClient;
import com.wokcontrol.domicilios.client.PedidosClient;
import com.wokcontrol.domicilios.client.QrRutaClient;
import com.wokcontrol.domicilios.client.UsuariosClient;
import com.wokcontrol.domicilios.dto.DomicilioDtos.CobroRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.DomicilioRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.EdicionRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.ItemRequest;
import com.wokcontrol.domicilios.dto.DomicilioDtos.MetodoCobro;
import com.wokcontrol.domicilios.dto.DomicilioDtos.TipoProducto;
import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.entity.EstadoDomicilio;
import com.wokcontrol.domicilios.repository.DomicilioRepository;
import com.wokcontrol.domicilios.service.CampoInvalidoException;
import com.wokcontrol.domicilios.service.DomicilioService;

/** v2 · Sprint 3 · Épica 9: criterios de aceptación de HU-047 a HU-053 sobre el servicio, con dependencias simuladas. */
@ExtendWith(OutputCaptureExtension.class)
class DomicilioServiceTest {

    private final Map<String, Domicilio> datos = new LinkedHashMap<>();
    private DomicilioRepository repo;
    private PedidosClient pedidos;
    private UsuariosClient usuarios;
    private FacturacionClient facturacion;
    private AuditoriaPublisher auditoria;
    private DomicilioService servicio;
    private String estadoCocina = "ENVIADO";

    @BeforeEach
    void preparar() {
        repo = mock(DomicilioRepository.class);
        when(repo.save(any(Domicilio.class))).thenAnswer(i -> {
            Domicilio d = i.getArgument(0);
            datos.put(d.getId(), d);
            return d;
        });
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(datos.get(i.<String>getArgument(0))));
        when(repo.countByDomiciliarioAndEstado(anyString(), any())).thenAnswer(i -> datos.values().stream()
                .filter(d -> i.getArgument(0).equals(d.getDomiciliario()) && d.getEstado() == i.getArgument(1)).count());

        pedidos = mock(PedidosClient.class);
        when(pedidos.crear(any())).thenReturn(new PedidosClient.PedidoCocina("p1", "P-0001", "ENVIADO",
                List.of(new PedidosClient.ItemCocina("arroz", "Arroz frito especial", new BigDecimal("12500"), 3,
                        new BigDecimal("37500"))),
                new BigDecimal("37500")));
        when(pedidos.estados(anyList())).thenAnswer(i -> Map.of("p1", estadoCocina));

        usuarios = mock(UsuariosClient.class);
        when(usuarios.domiciliarios()).thenReturn(List.of(new UsuariosClient.Persona("luis", "Luis Pabón"),
                new UsuariosClient.Persona("marta", "Marta Cárdenas")));

        facturacion = mock(FacturacionClient.class);
        when(facturacion.cobrar(any())).thenAnswer(i -> {
            FacturacionClient.CobroDomicilioRequest r = i.getArgument(0);
            BigDecimal recibido = r.pagos().get(0).monto();
            return new FacturacionClient.Comprobante("f1", "TIQ-0007", new BigDecimal("45000"), recibido,
                    recibido.subtract(new BigDecimal("45000")), r.cajero());
        });

        ParametrosClient parametros = mock(ParametrosClient.class);
        when(parametros.impuestoConsumoPorcentaje()).thenReturn(new BigDecimal("8"));
        SecuenciaService secuencias = mock(SecuenciaService.class);
        AtomicInteger n = new AtomicInteger();
        when(secuencias.siguienteFormateado(eq("domicilios"), eq("D-"))).thenAnswer(i -> "D-000" + n.incrementAndGet());
        QrRutaClient qr = mock(QrRutaClient.class);
        auditoria = mock(AuditoriaPublisher.class);

        servicio = new DomicilioService(repo, pedidos, usuarios, facturacion, parametros, qr, secuencias, auditoria);
        autenticar(Roles.CAJERO, "caja1");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String rol, String usuario) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(usuario).claim("nombre", "Nombre de " + usuario)
                .claim("roles", List.of(rol)).claim("permisos", List.copyOf(Roles.SISTEMA.get(rol).permisos())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                Roles.SISTEMA.get(rol).permisos().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private static DomicilioRequest solicitud(String telefono, String direccion) {
        return new DomicilioRequest("Ana Pérez", telefono, direccion, "Torre 2 apto 501", new BigDecimal("4500"),
                List.of(new ItemRequest(TipoProducto.PLATO, "arroz", 3, 1, null)), "Paga con billete de 50 mil");
    }

    private Domicilio registrado() {
        return servicio.registrar(solicitud("300 123 4567", "Calle 45 # 13-20"));
    }

    /** Registra, cocina lo marca listo, el cajero lo asigna a Luis y Luis sale: queda EN_CAMINO. */
    private Domicilio enCamino() {
        Domicilio d = registrado();
        estadoCocina = "LISTO";
        servicio.asignar(d.getId(), "luis");
        autenticar(Roles.DOMICILIARIO, "luis");
        return servicio.enCamino(d.getId(), UsuarioActual.requerido());
    }

    private static String codigo(Throwable ex) {
        return ((ReglaNegocioException) ex).getCodigo();
    }

    // ------------------------------------------------------------------ HU-047

    @Test
    void ac047_1_registraElDomicilioYElPedidoVaACocinaConElTotalCorrecto() {
        Domicilio d = registrado();

        assertThat(d.getEstado()).isEqualTo(EstadoDomicilio.RECIBIDO);
        assertThat(d.getNumero()).isEqualTo("D-0001");
        assertThat(d.getPedidoNumero()).isEqualTo("P-0001");
        assertThat(d.getCliente().telefono()).isEqualTo("3001234567");
        // platos $37.500 + impuesto al consumo 8 % ($3.000) + domicilio $4.500 = $45.000
        assertThat(d.getSubtotalPlatos()).isEqualByComparingTo("37500");
        assertThat(d.getImpuestoValor()).isEqualByComparingTo("3000");
        assertThat(d.getTotal()).isEqualByComparingTo("45000");
        assertThat(d.getRegistradoPor()).isEqualTo("caja1");

        ArgumentCaptor<PedidosClient.PedidoDomicilioRequest> pedido = ArgumentCaptor.forClass(PedidosClient.PedidoDomicilioRequest.class);
        verify(pedidos).crear(pedido.capture());
        assertThat(pedido.getValue().domicilioId()).isEqualTo(d.getId());
        assertThat(pedido.getValue().items()).hasSize(1);
    }

    @Test
    void ac047_2_telefonoODireccionInvalidosIndicanElCampoYNoCreanPedido() {
        assertThatThrownBy(() -> servicio.registrar(solicitud("12345", "Calle 45 # 13-20")))
                .isInstanceOfSatisfying(CampoInvalidoException.class, ex -> assertThat(ex.getCampo()).isEqualTo("telefono"));
        assertThatThrownBy(() -> servicio.registrar(solicitud("3001234567", "  ")))
                .isInstanceOfSatisfying(CampoInvalidoException.class, ex -> assertThat(ex.getCampo()).isEqualTo("direccion"));
        verify(pedidos, never()).crear(any());
        assertThat(datos).isEmpty();
    }

    @Test
    void ac047_3_seEditaAntesDelDespachoPeroNoDespues() {
        Domicilio d = registrado();
        Domicilio editado = servicio.editar(d.getId(), new EdicionRequest("Ana Pérez", "3001234567", "Carrera 27 # 30-10",
                null, new BigDecimal("6000"), null));
        assertThat(editado.getCliente().direccion()).isEqualTo("Carrera 27 # 30-10");
        assertThat(editado.getTotal()).isEqualByComparingTo("46500");

        Domicilio enRuta = enCamino();
        autenticar(Roles.CAJERO, "caja1");
        assertThatThrownBy(() -> servicio.editar(enRuta.getId(), new EdicionRequest("Ana", "3001234567", "Otra", null,
                BigDecimal.ZERO, null)))
                .satisfies(ex -> assertThat(codigo(ex)).isEqualTo(DomicilioService.YA_DESPACHADO));
    }

    // ------------------------------------------------------------------ HU-048

    @Test
    void ac048_1_y_ac048_2_asignaSoloCuandoCocinaLoTieneListo() {
        Domicilio d = registrado();
        estadoCocina = "EN_PREPARACION";
        assertThatThrownBy(() -> servicio.asignar(d.getId(), "luis"))
                .satisfies(ex -> assertThat(codigo(ex)).isEqualTo(DomicilioService.PEDIDO_NO_LISTO));
        assertThat(datos.get(d.getId()).getEstado()).isEqualTo(EstadoDomicilio.EN_PREPARACION);

        estadoCocina = "LISTO";
        Domicilio asignado = servicio.asignar(d.getId(), "luis");
        assertThat(asignado.getEstado()).isEqualTo(EstadoDomicilio.ASIGNADO);
        assertThat(asignado.getDomiciliarioNombre()).isEqualTo("Luis Pabón");
    }

    @Test
    void rn14_noAsignaAUnDomiciliarioQueEstaEnCamino() {
        enCamino(); // Luis ya salió con D-0001
        autenticar(Roles.CAJERO, "caja1");
        Domicilio otro = registrado();
        assertThat(servicio.domiciliarios()).filteredOn(x -> x.username().equals("luis"))
                .singleElement().satisfies(x -> assertThat(x.disponible()).isFalse());

        assertThatThrownBy(() -> servicio.asignar(otro.getId(), "luis"))
                .satisfies(ex -> assertThat(codigo(ex)).isEqualTo(DomicilioService.DOMICILIARIO_NO_DISPONIBLE));
        assertThat(servicio.asignar(otro.getId(), "marta").getDomiciliario()).isEqualTo("marta");
    }

    // ------------------------------------------------------------------ HU-049 / HU-051

    @Test
    void ac049_1_y_ac051_1_elDomiciliarioSoloVeLosSuyos() {
        Domicilio d = registrado();
        estadoCocina = "LISTO";
        servicio.asignar(d.getId(), "luis");

        autenticar(Roles.DOMICILIARIO, "marta");
        assertThatThrownBy(() -> servicio.obtenerPara(d.getId(), UsuarioActual.requerido()))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.enCamino(d.getId(), UsuarioActual.requerido()))
                .isInstanceOf(OperacionNoPermitidaException.class);

        autenticar(Roles.DOMICILIARIO, "luis");
        assertThat(servicio.obtenerPara(d.getId(), UsuarioActual.requerido()).getNumero()).isEqualTo("D-0001");
        servicio.mios("luis");
        verify(repo).findByDomiciliarioAndEstadoInOrderByAsignadoEnAsc("luis", EstadoDomicilio.DEL_DOMICILIARIO);
    }

    @Test
    void ac049_2_noSeEntregaSinHaberSalido() {
        Domicilio d = registrado();
        estadoCocina = "LISTO";
        servicio.asignar(d.getId(), "luis");
        autenticar(Roles.DOMICILIARIO, "luis");

        assertThatThrownBy(() -> servicio.entregar(d.getId(), new CobroRequest(MetodoCobro.EFECTIVO, new BigDecimal("45000"), null),
                UsuarioActual.requerido()))
                .satisfies(ex -> assertThat(codigo(ex)).isEqualTo(DomicilioService.TRANSICION_INVALIDA))
                .hasMessageContaining("en camino");
        verify(facturacion, never()).cobrar(any());
    }

    // ------------------------------------------------------------------ HU-050

    @Test
    void ac050_1_cobraEnEfectivoCalculaElCambioYFacturaAlCajero() {
        Domicilio d = enCamino();
        Domicilio entregado = servicio.entregar(d.getId(), new CobroRequest(MetodoCobro.EFECTIVO, new BigDecimal("50000"), null),
                UsuarioActual.requerido());

        assertThat(entregado.getEstado()).isEqualTo(EstadoDomicilio.ENTREGADO);
        assertThat(entregado.getCobro().cambio()).isEqualByComparingTo("5000");
        assertThat(entregado.getCobro().facturaNumero()).isEqualTo("TIQ-0007");
        verify(pedidos).entregar("p1");
        ArgumentCaptor<FacturacionClient.CobroDomicilioRequest> cobro = ArgumentCaptor.forClass(FacturacionClient.CobroDomicilioRequest.class);
        verify(facturacion).cobrar(cobro.capture());
        assertThat(cobro.getValue().cajero()).isEqualTo("caja1"); // AC-051.2: entra al cierre de caja de quien lo registró
        assertThat(cobro.getValue().domiciliario()).isEqualTo("luis");
        assertThat(cobro.getValue().valorDomicilio()).isEqualByComparingTo("4500");
    }

    @Test
    void ac050_2_pagoInsuficienteNoEntregaNiFactura() {
        Domicilio d = enCamino();
        assertThatThrownBy(() -> servicio.entregar(d.getId(), new CobroRequest(MetodoCobro.EFECTIVO, new BigDecimal("40000"), null),
                UsuarioActual.requerido()))
                .satisfies(ex -> assertThat(codigo(ex)).isEqualTo(DomicilioService.PAGO_INSUFICIENTE));
        assertThat(datos.get(d.getId()).getEstado()).isEqualTo(EstadoDomicilio.EN_CAMINO);
        verify(pedidos, never()).entregar(anyString());
        verify(facturacion, never()).cobrar(any());
    }

    @Test
    void conTarjetaSeCobraElValorExacto() {
        Domicilio d = enCamino();
        assertThatThrownBy(() -> servicio.entregar(d.getId(), new CobroRequest(MetodoCobro.TARJETA_DEBITO, new BigDecimal("50000"),
                "123456"), UsuarioActual.requerido()))
                .isInstanceOf(CampoInvalidoException.class);
    }

    // ------------------------------------------------------------------ HU-052

    @Test
    void ac052_1_noEntregadoAnulaElPedidoSinFactura() {
        Domicilio d = enCamino();
        Domicilio r = servicio.noEntregado(d.getId(), "El cliente no contesta el teléfono", UsuarioActual.requerido());

        assertThat(r.getEstado()).isEqualTo(EstadoDomicilio.NO_ENTREGADO);
        assertThat(r.getMotivoNoEntrega()).isEqualTo("El cliente no contesta el teléfono");
        verify(pedidos).anular(eq("p1"), anyString());
        verify(facturacion, never()).cobrar(any());
        verify(auditoria).registrar(eq("ESTADO_DOMICILIO"), eq("Domicilio"), eq("D-0001"),
                eq("EN_CAMINO → NO_ENTREGADO · El cliente no contesta el teléfono"));
    }

    @Test
    void ac052_2_elMotivoEsObligatorio() {
        Domicilio d = enCamino();
        assertThatThrownBy(() -> servicio.noEntregado(d.getId(), "no", UsuarioActual.requerido()))
                .satisfies(ex -> assertThat(codigo(ex)).isEqualTo(DomicilioService.MOTIVO_OBLIGATORIO));
        assertThat(datos.get(d.getId()).getEstado()).isEqualTo(EstadoDomicilio.EN_CAMINO);
        verify(pedidos, never()).anular(anyString(), anyString());
    }

    // ------------------------------------------------------------------ HU-053

    @Test
    void ac053_2_cadaCambioDeEstadoQuedaEnElHistorialYEnAuditoriaSinDatosPersonales() {
        Domicilio d = enCamino();
        servicio.entregar(d.getId(), new CobroRequest(MetodoCobro.EFECTIVO, new BigDecimal("45000"), null), UsuarioActual.requerido());

        List<Domicilio.CambioEstado> h = datos.get(d.getId()).getHistorial();
        assertThat(h).extracting(Domicilio.CambioEstado::nuevo).containsExactly(EstadoDomicilio.RECIBIDO, EstadoDomicilio.LISTO,
                EstadoDomicilio.ASIGNADO, EstadoDomicilio.EN_CAMINO, EstadoDomicilio.ENTREGADO);
        assertThat(h).allSatisfy(c -> {
            assertThat(c.fecha()).isNotNull();
            assertThat(c.usuario()).isNotBlank();
        });
        assertThat(h.get(3).usuario()).isEqualTo("luis");

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoria, atLeastOnce()).registrar(eq("ESTADO_DOMICILIO"), eq("Domicilio"), eq("D-0001"), detalle.capture());
        assertThat(detalle.getAllValues()).hasSize(5)
                .noneMatch(s -> s.contains("3001234567") || s.contains("Calle 45") || s.contains("Ana"));
    }

    @Test
    void elPedidoCanceladoEnCocinaCancelaElDomicilio() {
        Domicilio d = registrado();
        estadoCocina = "CANCELADO";
        servicio.sincronizarCocina(List.of(datos.get(d.getId())));
        assertThat(datos.get(d.getId()).getEstado()).isEqualTo(EstadoDomicilio.CANCELADO);
    }

    @Test
    void telefonoSeNormalizaADiezDigitos() {
        assertThat(DomicilioService.normalizarTelefono("+57 300-123-4567")).isEqualTo("3001234567");
        assertThat(DomicilioService.normalizarTelefono("300.123.45")).isNull();
    }

    /**
     * v2 · Sprint 4 · PBI-29 (Ley 1581 de 2012, constitución 6): en todo el ciclo del domicilio, incluidos el
     * cobro con diferencia, la caída de cocina y el no entregado, los registros no llevan el nombre, el teléfono
     * ni la dirección del cliente.
     */
    @Test
    void pbi29_losRegistrosNoLlevanDatosPersonalesDelCliente(CapturedOutput salida) {
        Domicilio d = enCamino();
        servicio.entregar(d.getId(), new CobroRequest(MetodoCobro.EFECTIVO, new BigDecimal("50000"), null), UsuarioActual.requerido());

        autenticar(Roles.CAJERO, "caja1");
        Domicilio otro = registrado();
        when(pedidos.estados(anyList())).thenThrow(new com.wokcontrol.common.exception.ServicioRemotoException(
                "pedidos-service", "no responde", null));
        servicio.sincronizarCocina(List.of(datos.get(otro.getId())));

        assertThat(salida.getAll()).doesNotContain("3001234567", "300 123 4567", "Calle 45", "13-20", "Ana Pérez",
                "Torre 2 apto 501");
    }
}
