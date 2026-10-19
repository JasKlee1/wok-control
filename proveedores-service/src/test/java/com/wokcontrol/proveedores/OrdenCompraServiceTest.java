package com.wokcontrol.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.exception.ServicioRemotoException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.proveedores.client.InventarioClient;
import com.wokcontrol.proveedores.client.InventarioClient.InsumoResumen;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ItemOrdenRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.LineaRecepcionRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.OrdenRequest;
import com.wokcontrol.proveedores.entity.OrdenCompra;
import com.wokcontrol.proveedores.entity.OrdenCompra.Estado;
import com.wokcontrol.proveedores.entity.Proveedor;
import com.wokcontrol.proveedores.repository.OrdenCompraRepository;
import com.wokcontrol.proveedores.repository.ProveedorRepository;
import com.wokcontrol.proveedores.service.OrdenCompraService;

class OrdenCompraServiceTest {

    private final Map<String, OrdenCompra> bd = new HashMap<>();
    private OrdenCompraRepository repo;
    private InventarioClient inventario;
    private OrdenCompraService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(OrdenCompraRepository.class);
        when(repo.save(any(OrdenCompra.class))).thenAnswer(i -> {
            OrdenCompra o = i.getArgument(0);
            if (o.getId() == null) {
                o.setId("oc1");
            }
            bd.put(o.getId(), o);
            return o;
        });
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(bd.get(i.<String>getArgument(0))));
        ProveedorRepository proveedores = mock(ProveedorRepository.class);
        Proveedor p = new Proveedor();
        p.setId("prov1");
        p.setNombreComercial("Distribuidora Oriente");
        p.setActivo(true);
        when(proveedores.findById("prov1")).thenReturn(Optional.of(p));
        inventario = mock(InventarioClient.class);
        when(inventario.insumos()).thenReturn(List.of(new InsumoResumen("arroz", "Arroz", "KG", "GRANOS",
                BigDecimal.ONE, BigDecimal.ZERO, true)));
        SecuenciaService sec = mock(SecuenciaService.class);
        when(sec.siguienteFormateado(anyString(), anyString())).thenReturn("OC-000001");
        servicio = new OrdenCompraService(repo, proveedores, inventario, sec, mock(AuditoriaPublisher.class), "America/Bogota");
        autenticar(Roles.INVENTARIO, null);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String rol, String proveedorId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("u-" + rol).claim("nombre", "U")
                .claim("roles", List.of(rol)).claim("permisos", List.copyOf(Roles.SISTEMA.get(rol).permisos()));
        if (proveedorId != null) {
            b.claim("proveedorId", proveedorId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(b.build(),
                Roles.SISTEMA.get(rol).permisos().stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private OrdenCompra ordenEnviada() {
        servicio.crear(new OrdenRequest("prov1", List.of(new ItemOrdenRequest("arroz", new BigDecimal("50"), new BigDecimal("4200"))), null, null));
        return servicio.enviar("oc1");
    }

    @Test
    void ordenNuevaQuedaEnBorradorConTotalCalculado() {
        OrdenCompra o = servicio.crear(new OrdenRequest("prov1",
                List.of(new ItemOrdenRequest("arroz", new BigDecimal("50"), new BigDecimal("4200"))), null, "Urgente"));
        assertThat(o.getEstado()).isEqualTo(Estado.BORRADOR);
        assertThat(o.getTotal()).isEqualByComparingTo("210000");
        verify(inventario, never()).registrarRecepcion(any());
    }

    @Test
    void proveedorSoloVeYConfirmaSusPropiasOrdenes() {
        ordenEnviada();
        autenticar(Roles.PROVEEDOR, "otro-proveedor");
        assertThatThrownBy(() -> servicio.confirmar("oc1")).isInstanceOf(RecursoNoEncontradoException.class);

        autenticar(Roles.PROVEEDOR, "prov1");
        assertThat(servicio.confirmar("oc1").getEstado()).isEqualTo(Estado.CONFIRMADA);
    }

    @Test
    void reportarEntregaNoModificaInventario() {
        ordenEnviada();
        autenticar(Roles.PROVEEDOR, "prov1");
        servicio.confirmar("oc1");
        OrdenCompra o = servicio.reportarEntrega("oc1", new com.wokcontrol.proveedores.dto.ProveedorDtos.EntregaRequest(
                java.time.LocalDate.now(), "REM-55", null));
        assertThat(o.getEstado()).isEqualTo(Estado.ENTREGA_REPORTADA);
        verify(inventario, never()).registrarRecepcion(any());
    }

    @Test
    void recepcionIngresaAInventarioSoloLoRecibidoConReferenciaDeLaOrden() {
        ordenEnviada();
        OrdenCompra o = servicio.recibir("oc1", List.of(new LineaRecepcionRequest("arroz", new BigDecimal("45"), "L-1", null)), "Faltaron 5 kg");

        assertThat(o.getEstado()).isEqualTo(Estado.RECIBIDA);
        assertThat(o.getRecepcion().sincronizadaInventario()).isTrue();
        assertThat(o.getTotalRecibido()).isEqualByComparingTo("189000");
        ArgumentCaptor<InventarioClient.RecepcionRequest> req = ArgumentCaptor.forClass(InventarioClient.RecepcionRequest.class);
        verify(inventario).registrarRecepcion(req.capture());
        assertThat(req.getValue().referencia()).isEqualTo("OC-oc1");
        assertThat(req.getValue().lineas()).singleElement().satisfies(l -> {
            assertThat(l.cantidad()).isEqualByComparingTo("45");
            assertThat(l.costoUnitario()).isEqualByComparingTo("4200");
        });
    }

    @Test
    void noSeRecibeMasDeLoPedidoNiOrdenesEnBorrador() {
        servicio.crear(new OrdenRequest("prov1", List.of(new ItemOrdenRequest("arroz", BigDecimal.TEN, BigDecimal.ONE)), null, null));
        assertThatThrownBy(() -> servicio.recibir("oc1", List.of(new LineaRecepcionRequest("arroz", BigDecimal.ONE, null, null)), null))
                .isInstanceOf(ReglaNegocioException.class);
        servicio.enviar("oc1");
        assertThatThrownBy(() -> servicio.recibir("oc1", List.of(new LineaRecepcionRequest("arroz", new BigDecimal("11"), null, null)), null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("más");
    }

    @Test
    void siInventarioFallaLaRecepcionQuedaPendienteYElReintentoUsaLosMismosDatos() {
        ordenEnviada();
        when(inventario.registrarRecepcion(any()))
                .thenThrow(new ServicioRemotoException("inventario-service", "caído", null))
                .thenReturn(new InventarioClient.ResultadoOperacion("OC-oc1", "APLICADO", false, 1));

        assertThatThrownBy(() -> servicio.recibir("oc1", List.of(new LineaRecepcionRequest("arroz", new BigDecimal("50"), null, null)), null))
                .isInstanceOf(ServicioRemotoException.class);
        OrdenCompra pendiente = servicio.obtener("oc1");
        assertThat(pendiente.getEstado()).isEqualTo(Estado.ENVIADA);
        assertThat(pendiente.getRecepcion().sincronizadaInventario()).isFalse();
        assertThatThrownBy(() -> servicio.recibir("oc1", List.of(new LineaRecepcionRequest("arroz", BigDecimal.ONE, null, null)), null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pendiente");

        assertThat(servicio.reintentarSincronizacion("oc1").getEstado()).isEqualTo(Estado.RECIBIDA);
        verify(inventario, times(2)).registrarRecepcion(any());
    }

    @Test
    void cancelarExigeMotivoYNoAplicaAOrdenesRecibidas() {
        ordenEnviada();
        assertThatThrownBy(() -> servicio.cancelar("oc1", "x")).isInstanceOf(ReglaNegocioException.class);
        servicio.recibir("oc1", List.of(new LineaRecepcionRequest("arroz", BigDecimal.TEN, null, null)), null);
        assertThatThrownBy(() -> servicio.cancelar("oc1", "Ya no se necesita")).isInstanceOf(ReglaNegocioException.class);
    }
}
