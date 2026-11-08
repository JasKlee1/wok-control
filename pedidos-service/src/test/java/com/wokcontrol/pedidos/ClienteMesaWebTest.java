package com.wokcontrol.pedidos;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.client.MenuClient.ProductoCarta;
import com.wokcontrol.pedidos.client.MenuClient.SeccionCarta;
import com.wokcontrol.pedidos.config.SecurityConfig;
import com.wokcontrol.pedidos.controller.api.MesaApiRestController;
import com.wokcontrol.pedidos.controller.api.PedidoApiRestController;
import com.wokcontrol.pedidos.controller.web.ClienteMesaWebController;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;
import com.wokcontrol.pedidos.service.ResumenService;
import com.wokcontrol.pedidos.service.SesionMesaService;
import com.wokcontrol.pedidos.service.SesionMesaService.MesaNoDisponibleException;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionAbierta;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionVencidaException;

import jakarta.servlet.http.Cookie;

/**
 * Épica 7 · T001 (HU-036: rol CLIENTE y sesión de mesa) y T003 (HU-033: carta por QR), en la capa
 * web y de seguridad. Se escribieron antes que {@link ClienteMesaWebController}.
 */
@WebMvcTest(controllers = { ClienteMesaWebController.class, MesaApiRestController.class, PedidoApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class ClienteMesaWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    SesionMesaService sesiones;
    @MockitoBean
    MenuClient menu;
    @MockitoBean
    PedidoService pedidos;
    @MockitoBean
    MesaService mesas;
    @MockitoBean
    ResumenService resumen;

    private String tokenMesa5() {
        return tokens.emitirTokenMesa("s1", "m5", 5).token();
    }

    private static SesionMesa sesionActiva() {
        SesionMesa s = new SesionMesa();
        s.setId("s1");
        s.setMesaId("m5");
        s.setMesaNumero(5);
        s.setEstado(SesionMesa.Estado.ACTIVA);
        return s;
    }

    @Test
    void qrValidoAbreLaSesionYLlevaALaCarta() throws Exception {
        var emitido = tokens.emitirTokenMesa("s1", "m5", 5);
        when(sesiones.abrir("qr-5")).thenReturn(new SesionAbierta("s1", 5, emitido.token(), emitido.expiraEn()));

        mvc.perform(get("/cliente/mesa/qr-5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/carta"))
                .andExpect(cookie().value("WOK_TOKEN", emitido.token()))
                .andExpect(cookie().httpOnly("WOK_TOKEN", true));
    }

    @Test
    void qrFuncionaAunqueElCelularTengaUnaCookieVencida() throws Exception {
        var emitido = tokens.emitirTokenMesa("s1", "m5", 5);
        when(sesiones.abrir("qr-5")).thenReturn(new SesionAbierta("s1", 5, emitido.token(), emitido.expiraEn()));

        mvc.perform(get("/cliente/mesa/qr-5").cookie(new Cookie("WOK_TOKEN", "token-vencido")))
                .andExpect(redirectedUrl("/cliente/carta"));
    }

    @Test
    void ac033_2_qrDeMesaNoDisponibleMuestraMensajeYNoAbreSesion() throws Exception {
        when(sesiones.abrir("no-existe")).thenThrow(new MesaNoDisponibleException());

        mvc.perform(get("/cliente/mesa/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(Matchers.containsString("Mesa no disponible, pide ayuda al personal")))
                .andExpect(cookie().doesNotExist("WOK_TOKEN"));
    }

    @Test
    void ac033_1_cartaMuestraLosPlatosDisponiblesConFotoYPrecio() throws Exception {
        when(sesiones.validar("s1", "m5")).thenReturn(sesionActiva());
        when(menu.carta()).thenReturn(List.of(new SeccionCarta("c1", "Arroces", List.of(
                new ProductoCarta("PLATO", "p1", "Arroz chino especial", "Con cerdo y camarón", new BigDecimal("28000"), 1,
                        Set.of("MARISCOS"), "img1"),
                new ProductoCarta("PLATO", "p2", "Arroz frito con piña", null, new BigDecimal("24000"), 0, Set.of(), null)))));

        mvc.perform(get("/cliente/carta").cookie(new Cookie("WOK_TOKEN", tokenMesa5())))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Mesa 5")))
                .andExpect(content().string(Matchers.containsString("Arroz chino especial")))
                .andExpect(content().string(Matchers.containsString("Arroz frito con piña")))
                .andExpect(content().string(Matchers.containsString("28.000")))
                .andExpect(content().string(Matchers.containsString("/menu/imagenes/img1")));
    }

    @Test
    void ac036_2_sesionVencidaPideEscanearElQrDeNuevo() throws Exception {
        when(sesiones.validar(anyString(), anyString())).thenThrow(new SesionVencidaException());

        mvc.perform(get("/cliente/carta").cookie(new Cookie("WOK_TOKEN", tokenMesa5())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(Matchers.containsString("Escanea el QR de tu mesa")))
                .andExpect(cookie().maxAge("WOK_TOKEN", 0));
    }

    @Test
    void ac036_1_clienteNoConsultaPedidosNiMesasCambiandoElIdentificador() throws Exception {
        String bearer = "Bearer " + tokenMesa5();
        mvc.perform(get("/api/mesas/m7/pedidos").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/mesas/m7").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/p1").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/mesas").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void clienteNoEntraALasPantallasDelPersonal() throws Exception {
        mvc.perform(get("/mesas").cookie(new Cookie("WOK_TOKEN", tokenMesa5())))
                .andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/pedidos").cookie(new Cookie("WOK_TOKEN", tokenMesa5())))
                .andExpect(redirectedUrl("/acceso-denegado"));
    }

    @Test
    void personalNoUsaLaCartaDelCliente() throws Exception {
        String mesero = tokens.emitirTokenUsuario("ana", "Ana", List.of(Roles.MESERO),
                Roles.SISTEMA.get(Roles.MESERO).permisos(), null).token();
        mvc.perform(get("/cliente/carta").cookie(new Cookie("WOK_TOKEN", mesero)))
                .andExpect(redirectedUrl("/acceso-denegado"));
    }
}
