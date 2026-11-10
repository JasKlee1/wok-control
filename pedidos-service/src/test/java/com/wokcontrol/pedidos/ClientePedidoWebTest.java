package com.wokcontrol.pedidos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.config.SecurityConfig;
import com.wokcontrol.pedidos.controller.web.ClienteMesaWebController;
import com.wokcontrol.pedidos.controller.web.PedidoWebController;
import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.ItemPedido;
import com.wokcontrol.pedidos.entity.Pedido;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoClienteService;
import com.wokcontrol.pedidos.service.PedidoClienteService.Carrito;
import com.wokcontrol.pedidos.service.PedidoClienteService.LineaVista;
import com.wokcontrol.pedidos.service.PedidoService;
import com.wokcontrol.pedidos.service.SesionMesaService;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionVencidaException;

import jakarta.servlet.http.Cookie;

/**
 * Épica 7 · Capa web y de seguridad de lo que el cliente hace desde el celular (T005, T007, T011,
 * T013) y de la confirmación del mesero (T009).
 */
@WebMvcTest(controllers = { ClienteMesaWebController.class, PedidoWebController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class ClientePedidoWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    SesionMesaService sesiones;
    @MockitoBean
    PedidoClienteService cliente;
    @MockitoBean
    MenuClient menu;
    @MockitoBean
    PedidoService pedidos;
    @MockitoBean
    MesaService mesas;

    private SesionMesa sesion;

    private Cookie mesa5() {
        return new Cookie("WOK_TOKEN", tokens.emitirTokenMesa("s1", "m5", 5).token());
    }

    private Cookie mesero() {
        return new Cookie("WOK_TOKEN", tokens.emitirTokenUsuario("ana", "Ana", List.of(Roles.MESERO),
                Roles.SISTEMA.get(Roles.MESERO).permisos(), null).token());
    }

    @BeforeEach
    void sesionActiva() {
        sesion = new SesionMesa();
        sesion.setId("s1");
        sesion.setMesaId("m5");
        sesion.setMesaNumero(5);
        when(sesiones.validar("s1", "m5")).thenReturn(sesion);
        when(cliente.carrito(any())).thenReturn(new Carrito(List.of(), BigDecimal.ZERO, 0, false));
        when(cliente.huella(any())).thenReturn("h1");
    }

    private static Pedido pedido(EstadoPedido estado, String motivo) {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setNumero("P-000123");
        p.setMesaId("m5");
        p.setEstado(estado);
        p.setMotivoRechazo(motivo);
        ItemPedido i = new ItemPedido();
        i.setNombre("Arroz chino especial");
        i.setCantidad(2);
        i.setPrecioUnitario(new BigDecimal("28000"));
        p.getItems().add(i);
        return p;
    }

    @Test
    void hu032_agregarDesdeLaCartaUsaLaSesionDelToken() throws Exception {
        when(cliente.agregar(eq(sesion), eq("PLATO"), eq("arroz"), eq(1))).thenReturn(sesion);

        mvc.perform(post("/cliente/carrito").param("tipo", "PLATO").param("productoId", "arroz").with(csrf()).cookie(mesa5()))
                .andExpect(redirectedUrl("/cliente/carta"));
        verify(cliente).agregar(sesion, "PLATO", "arroz", 1);
    }

    @Test
    void ac032_1_cambiarCantidadYObservacion() throws Exception {
        mvc.perform(post("/cliente/carrito/l1").param("cantidad", "3").param("notas", "sin cebolla").with(csrf()).cookie(mesa5()))
                .andExpect(redirectedUrl("/cliente/pedido"));
        verify(cliente).cambiar(sesion, "l1", 3, "sin cebolla");
    }

    @Test
    void ac032_2_quitarUnPlatoEnviadoMuestraElMensaje() throws Exception {
        org.mockito.Mockito.doThrow(new ReglaNegocioException(PedidoClienteService.MENSAJE_YA_ENVIADO))
                .when(cliente).modificarEnviado(sesion, "p1");

        mvc.perform(post("/cliente/pedidos/p1/items/i1/quitar").with(csrf()).cookie(mesa5()))
                .andExpect(redirectedUrl("/cliente/pedido"))
                .andExpect(flash().attribute("error", "El pedido ya fue enviado; pide el cambio al mesero"));
    }

    @Test
    void ac036_1_elClienteRecibe403SiTocaUnPedidoDeOtraMesa() throws Exception {
        org.mockito.Mockito.doThrow(new OperacionNoPermitidaException(PedidoClienteService.MENSAJE_OTRA_MESA))
                .when(cliente).modificarEnviado(sesion, "p7");

        mvc.perform(post("/cliente/pedidos/p7/items/i1/quitar").with(csrf()).cookie(mesa5()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(Matchers.containsString("Ese pedido no es de tu mesa")));
    }

    @Test
    void ac034_3_enviarVacioMuestraElMensaje() throws Exception {
        when(cliente.enviar(sesion)).thenThrow(new ReglaNegocioException("Agrega al menos un plato"));

        mvc.perform(post("/cliente/pedido/enviar").with(csrf()).cookie(mesa5()))
                .andExpect(redirectedUrl("/cliente/pedido"))
                .andExpect(flash().attribute("error", "Agrega al menos un plato"));
    }

    @Test
    void ac034_2_miPedidoMuestraElEstadoDeCadaPlato() throws Exception {
        Pedido p = pedido(EstadoPedido.EN_PREPARACION, null);
        p.getItems().get(0).setEstado(ItemPedido.Estado.LISTO);
        when(cliente.pedidosDeSesion(sesion)).thenReturn(List.of(p));

        mvc.perform(get("/cliente/pedido").cookie(mesa5()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Pedido P-000123")))
                .andExpect(content().string(Matchers.containsString("2 × Arroz chino especial")))
                .andExpect(content().string(Matchers.containsString(">Listo<")));
    }

    @Test
    void ac037_2_elClienteVeElMotivoDelRechazo() throws Exception {
        when(cliente.pedidosDeSesion(sesion)).thenReturn(List.of(pedido(EstadoPedido.RECHAZADO, "La mesa está vacía")));

        mvc.perform(get("/cliente/pedido").cookie(mesa5()))
                .andExpect(content().string(Matchers.containsString("Pedido rechazado: La mesa está vacía")));
    }

    @Test
    void ac038_1_elClienteVeElAvisoDePlatoAgotado() throws Exception {
        when(cliente.carrito(sesion)).thenReturn(new Carrito(List.of(new LineaVista("l1", "Pato laqueado", 1, null,
                new BigDecimal("52000"), new BigDecimal("52000"), true, "Plato agotado")), BigDecimal.ZERO, 0, true));

        mvc.perform(get("/cliente/pedido").cookie(mesa5()))
                .andExpect(content().string(Matchers.containsString("Un plato de tu pedido se agotó")))
                .andExpect(content().string(Matchers.containsString("Pato laqueado")))
                .andExpect(content().string(Matchers.containsString("Agotado: ya no está disponible")));
    }

    @Test
    void ac038_huellaParaQueElCelularSeActualiceSolo() throws Exception {
        mvc.perform(get("/cliente/estado").cookie(mesa5()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huella").value("h1"));
    }

    @Test
    void ac035_pedirLaCuentaConfirmaElTotal() throws Exception {
        when(cliente.pedirCuenta(sesion)).thenReturn(new PedidoClienteService.CuentaSolicitada(5, new BigDecimal("68000"), 2));

        mvc.perform(post("/cliente/cuenta").with(csrf()).cookie(mesa5()))
                .andExpect(redirectedUrl("/cliente/pedido"))
                .andExpect(flash().attribute("exito", Matchers.containsString("68.000")))
                .andExpect(flash().attribute("exito", Matchers.containsString("2 plato(s) por llegar")));
    }

    @Test
    void ac036_2_conLaSesionVencidaNoSeAgreganPlatos() throws Exception {
        when(sesiones.validar(anyString(), anyString())).thenThrow(new SesionVencidaException());

        mvc.perform(post("/cliente/carrito").param("tipo", "PLATO").param("productoId", "arroz").with(csrf()).cookie(mesa5()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(Matchers.containsString("Escanea el QR de tu mesa")));
        verify(cliente, never()).agregar(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void elMeseroNoUsaLasRutasDelCliente() throws Exception {
        mvc.perform(post("/cliente/pedido/enviar").with(csrf()).cookie(mesero())).andExpect(redirectedUrl("/acceso-denegado"));
    }

    // ------------------------------------------------------------------ HU-037 · mesero

    @Test
    void ac037_1_elMeseroConfirmaElPedidoQr() throws Exception {
        when(pedidos.confirmar("p1")).thenReturn(pedido(EstadoPedido.ENVIADO, null));

        mvc.perform(post("/pedidos/p1/confirmar").with(csrf()).cookie(mesero()))
                .andExpect(redirectedUrl("/pedidos/p1"))
                .andExpect(flash().attribute("exito", Matchers.containsString("enviado a cocina")));
    }

    @Test
    void ac037_2_elMeseroRechazaSinMotivoYVeElMensaje() throws Exception {
        when(pedidos.rechazar("p1", null)).thenThrow(new ReglaNegocioException(PedidoService.MENSAJE_MOTIVO_RECHAZO));

        mvc.perform(post("/pedidos/p1/rechazar").with(csrf()).cookie(mesero()))
                .andExpect(flash().attribute("error", "Indica el motivo del rechazo"));
    }

    @Test
    void elClienteNoConfirmaNiRechazaPedidos() throws Exception {
        mvc.perform(post("/pedidos/p1/confirmar").with(csrf()).cookie(mesa5())).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(post("/pedidos/p1/rechazar").param("motivo", "x").with(csrf()).cookie(mesa5()))
                .andExpect(redirectedUrl("/acceso-denegado"));
        verify(pedidos, never()).confirmar(anyString());
    }
}
