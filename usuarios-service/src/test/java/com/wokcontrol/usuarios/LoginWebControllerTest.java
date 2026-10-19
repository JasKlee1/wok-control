package com.wokcontrol.usuarios;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.common.web.Formato;
import com.wokcontrol.usuarios.config.SecurityConfig;
import com.wokcontrol.usuarios.controller.web.LoginWebController;
import com.wokcontrol.usuarios.dto.AuthDtos.PerfilResponse;
import com.wokcontrol.usuarios.service.AuthService;
import com.wokcontrol.usuarios.service.CredencialesInvalidasException;

@WebMvcTest(controllers = LoginWebController.class)
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class, Formato.class })
class LoginWebControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AuthService auth;
    @MockitoBean
    AuditoriaPublisher auditoria;

    private void loginValido(boolean debeCambiar) {
        when(auth.autenticar("mesero1", "Clave1234")).thenReturn(new AuthService.Sesion(
                new TokenService.TokenEmitido("jwt", Instant.now().plusSeconds(600)),
                new PerfilResponse("mesero1", "Ana", null, List.of("MESERO"), Set.of(), null, debeCambiar)));
    }

    @Test
    void paginaDeLoginEsPublica() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
    }

    @Test
    void loginSinCsrfEsRechazado() throws Exception {
        mvc.perform(post("/login").param("username", "mesero1").param("password", "Clave1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/acceso-denegado"));
    }

    @Test
    void loginExitosoEntregaCookieHttpOnlyYRedirigeAlDestinoInterno() throws Exception {
        loginValido(false);
        mvc.perform(post("/login").with(csrf()).param("username", "mesero1").param("password", "Clave1234")
                .param("continuar", "/pedidos"))
                .andExpect(redirectedUrl("/pedidos"))
                .andExpect(cookie().value("WOK_TOKEN", "jwt"))
                .andExpect(cookie().httpOnly("WOK_TOKEN", true))
                .andExpect(cookie().path("WOK_TOKEN", "/"));
    }

    @Test
    void noPermiteRedireccionesAbiertas() throws Exception {
        loginValido(false);
        mvc.perform(post("/login").with(csrf()).param("username", "mesero1").param("password", "Clave1234")
                .param("continuar", "//sitio-malicioso.com"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void contrasenaTemporalObligaACambiarla() throws Exception {
        loginValido(true);
        mvc.perform(post("/login").with(csrf()).param("username", "mesero1").param("password", "Clave1234"))
                .andExpect(redirectedUrl("/perfil?cambioObligatorio"));
    }

    @Test
    void credencialesInvalidasMuestranElFormularioConError() throws Exception {
        when(auth.autenticar(anyString(), anyString())).thenThrow(new CredencialesInvalidasException("Usuario o contraseña incorrectos"));
        mvc.perform(post("/login").with(csrf()).param("username", "mesero1").param("password", "mala"))
                .andExpect(status().isUnauthorized())
                .andExpect(view().name("login"));
    }

    @Test
    void paginasProtegidasRedirigenAlLogin() throws Exception {
        mvc.perform(get("/acceso-denegado"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?continuar=%2Facceso-denegado"));
    }
}
