package com.wokcontrol.domicilios;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.domicilios.client.MenuClient;
import com.wokcontrol.domicilios.client.QrRutaClient;
import com.wokcontrol.domicilios.config.SecurityConfig;
import com.wokcontrol.domicilios.controller.api.DomicilioApiRestController;
import com.wokcontrol.domicilios.controller.web.DomicilioWebController;
import com.wokcontrol.domicilios.controller.web.EntregaWebController;
import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.entity.EstadoDomicilio;
import com.wokcontrol.domicilios.service.DomicilioService;

import jakarta.servlet.http.Cookie;

/** v2 · Sprint 3: permisos por rol (RN-15), validación de AC-047.2, consulta de AC-053.1 y el QR de la API externa. */
@WebMvcTest(controllers = { DomicilioApiRestController.class, DomicilioWebController.class, EntregaWebController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class })
class DomiciliosSecurityTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 };

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    DomicilioService servicio;
    @MockitoBean
    MenuClient menu;
    @MockitoBean
    QrRutaClient qr;

    private String jwt(String rol, String usuario) {
        return tokens.emitirTokenUsuario(usuario, usuario, List.of(rol), Roles.SISTEMA.get(rol).permisos(), null).token();
    }

    private String bearer(String rol) {
        return "Bearer " + jwt(rol, rol.toLowerCase());
    }

    private Cookie cookie(String rol) {
        return new Cookie("WOK_TOKEN", jwt(rol, rol.toLowerCase()));
    }

    private static Domicilio domicilio() {
        Domicilio d = new Domicilio();
        d.setId("d1");
        d.setNumero("D-0001");
        d.setCliente(new Domicilio.Cliente("Ana Pérez", "3001234567", "Calle 45 # 13-20", null));
        d.setTotal(new BigDecimal("45000"));
        d.cambiarEstado(EstadoDomicilio.RECIBIDO, "caja1", "Registrado");
        d.cambiarEstado(EstadoDomicilio.LISTO, "cocina", "Avance de cocina");
        return d;
    }

    @Test
    void domiciliarioNoRegistraNiVeTodosLosDomicilios() throws Exception {
        String t = bearer(Roles.DOMICILIARIO);
        mvc.perform(get("/api/domicilios").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/domicilios/domiciliarios").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(post("/api/domicilios").header("Authorization", t).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/domicilios/d1/asignacion").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"domiciliario\":\"luis\"}")).andExpect(status().isForbidden());
    }

    @Test
    void cajeroNoMarcaEntregasYMeseroNoEntraAlModulo() throws Exception {
        mvc.perform(post("/api/domicilios/d1/en-camino").header("Authorization", bearer(Roles.CAJERO))).andExpect(status().isForbidden());
        mvc.perform(get("/api/domicilios/mios").header("Authorization", bearer(Roles.CAJERO))).andExpect(status().isForbidden());
        mvc.perform(get("/api/domicilios").header("Authorization", bearer(Roles.MESERO))).andExpect(status().isForbidden());
        mvc.perform(get("/api/domicilios/d1").header("Authorization", bearer(Roles.COCINERO))).andExpect(status().isForbidden());
        mvc.perform(get("/api/domicilios")).andExpect(status().isUnauthorized());
    }

    @Test
    void ac047_2_validacionIndicaTelefonoYDireccion() throws Exception {
        mvc.perform(post("/api/domicilios").header("Authorization", bearer(Roles.CAJERO)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cliente\":\"Ana\",\"telefono\":\"123\",\"direccion\":\"\",\"valorDomicilio\":4500,"
                        + "\"items\":[{\"tipo\":\"PLATO\",\"productoId\":\"arroz\",\"cantidad\":1,\"nivelPicante\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.telefono").value("El teléfono debe tener 10 dígitos"))
                .andExpect(jsonPath("$.errores.direccion").exists());
        verify(servicio, never()).registrar(any());
    }

    @Test
    void ac053_1_elCajeroConsultaElEstadoActual() throws Exception {
        when(servicio.obtenerPara(eq("d1"), any())).thenReturn(domicilio());
        when(servicio.urlRuta(any())).thenReturn("https://www.google.com/maps/dir/?api=1&destination=x");
        mvc.perform(get("/api/domicilios/d1").header("Authorization", bearer(Roles.CAJERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("D-0001"))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.estadoEtiqueta").exists())
                .andExpect(jsonPath("$.urlRuta").exists());
    }

    @Test
    void ac051_1_domiciliarioAjenoRecibe403() throws Exception {
        when(servicio.obtenerPara(eq("d1"), any())).thenThrow(new OperacionNoPermitidaException("El domicilio D-0001 no está asignado a usted"));
        mvc.perform(get("/api/domicilios/d1").header("Authorization", bearer(Roles.DOMICILIARIO))).andExpect(status().isForbidden());
    }

    @Test
    void qrDeLaRutaSeSirveComoPngO503SiLaApiExternaFalla() throws Exception {
        when(servicio.qrRuta(eq("d1"), any())).thenReturn(Optional.of(PNG));
        mvc.perform(get("/api/domicilios/d1/qr-ruta").header("Authorization", bearer(Roles.DOMICILIARIO)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(content().bytes(PNG));
        mvc.perform(get("/domicilios/d1/qr-ruta.png").cookie(cookie(Roles.DOMICILIARIO)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        when(servicio.qrRuta(eq("d2"), any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/domicilios/d2/qr-ruta").header("Authorization", bearer(Roles.CAJERO)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void pantallasWebSegunElRol() throws Exception {
        Cookie domiciliario = cookie(Roles.DOMICILIARIO);
        mvc.perform(get("/domicilios").cookie(domiciliario)).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/domicilios/nuevo").cookie(domiciliario)).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(post("/domicilios/d1/asignar").with(csrf()).cookie(domiciliario)).andExpect(redirectedUrl("/acceso-denegado"));

        Cookie cajero = cookie(Roles.CAJERO);
        mvc.perform(get("/mis-entregas").cookie(cajero)).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(post("/mis-entregas/d1/en-camino").with(csrf()).cookie(cajero)).andExpect(redirectedUrl("/acceso-denegado"));
    }

    /**
     * v2 · Sprint 4 · PBI-29 (hallazgo CSRF de la v1): con la cookie de sesión válida, un POST sin token CSRF
     * se rechaza y no llega al servicio; con el token, sí.
     */
    @Test
    void pbi29_unPostWebConCookiePeroSinTokenCsrfSeRechaza() throws Exception {
        Cookie domiciliario = cookie(Roles.DOMICILIARIO);
        mvc.perform(post("/mis-entregas/d1/en-camino").cookie(domiciliario)).andExpect(redirectedUrl("/acceso-denegado"));
        verify(servicio, never()).enCamino(any(), any());

        when(servicio.enCamino(eq("d1"), any())).thenReturn(domicilio());
        mvc.perform(post("/mis-entregas/d1/en-camino").with(csrf()).cookie(domiciliario)).andExpect(redirectedUrl("/mis-entregas"));
        verify(servicio).enCamino(eq("d1"), any());
    }

    /** v2 · Sprint 4 · PBI-29: un token CSRF falso tampoco pasa. */
    @Test
    void pbi29_unTokenCsrfInvalidoSeRechaza() throws Exception {
        mvc.perform(post("/mis-entregas/d1/en-camino").with(csrf().useInvalidToken()).cookie(cookie(Roles.DOMICILIARIO)))
                .andExpect(redirectedUrl("/acceso-denegado"));
        verify(servicio, never()).enCamino(any(), any());
    }
}
