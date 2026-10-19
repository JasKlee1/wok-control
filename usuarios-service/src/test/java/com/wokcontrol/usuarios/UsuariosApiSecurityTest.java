package com.wokcontrol.usuarios;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.config.PaginacionConfig;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.usuarios.config.SecurityConfig;
import com.wokcontrol.usuarios.controller.api.AuditoriaApiRestController;
import com.wokcontrol.usuarios.controller.api.AuthApiRestController;
import com.wokcontrol.usuarios.controller.api.UsuarioApiRestController;
import com.wokcontrol.usuarios.dto.AuthDtos.PerfilResponse;
import com.wokcontrol.usuarios.entity.Usuario;
import com.wokcontrol.usuarios.service.AuditoriaService;
import com.wokcontrol.usuarios.service.AuthService;
import com.wokcontrol.usuarios.service.CredencialesInvalidasException;
import com.wokcontrol.usuarios.service.UsuarioService;

/**
 * Seguridad por permisos y contrato HTTP de los controladores REST, sin base de datos.
 */
@WebMvcTest(controllers = { UsuarioApiRestController.class, AuthApiRestController.class, AuditoriaApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class, PaginacionConfig.class })
class UsuariosApiSecurityTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    UsuarioService usuarios;
    @MockitoBean
    AuthService auth;
    @MockitoBean
    AuditoriaService auditoria;

    private String token(String rol) {
        return "Bearer " + tokens.emitirTokenUsuario("u-" + rol, "Prueba", List.of(rol),
                Roles.SISTEMA.get(rol).permisos(), null).token();
    }

    @Test
    void sinTokenResponde401EnJson() throws Exception {
        mvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenAlteradoEsRechazado() throws Exception {
        String t = token(Roles.ADMIN_PLATAFORMA);
        mvc.perform(get("/api/usuarios").header("Authorization", t.substring(0, t.length() - 3) + "abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meseroNoPuedeGestionarUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", token(Roles.MESERO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void administradorOperativoTampocoGestionaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization", token(Roles.ADMINISTRADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPlataformaListaUsuarios() throws Exception {
        Usuario u = new Usuario();
        u.setId("1");
        u.setUsername("cajero1");
        u.setNombre("Luis");
        u.setRoles(Set.of(Roles.CAJERO));
        u.setPasswordHash("{bcrypt}secreto");
        when(usuarios.buscar(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(u)));

        mvc.perform(get("/api/usuarios").header("Authorization", token(Roles.ADMIN_PLATAFORMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("cajero1"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void crearUsuarioValidaElCuerpo() throws Exception {
        mvc.perform(post("/api/usuarios").header("Authorization", token(Roles.ADMIN_PLATAFORMA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"A B\",\"nombre\":\"\",\"email\":\"no-es-correo\",\"password\":\"123\",\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.username").exists())
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.email").exists())
                .andExpect(jsonPath("$.errores.password").exists())
                .andExpect(jsonPath("$.errores.roles").exists());
    }

    @Test
    void loginEsPublicoYDevuelveToken() throws Exception {
        var emitido = new TokenService.TokenEmitido("jwt-de-prueba", Instant.now().plusSeconds(60));
        when(auth.autenticar("mesero1", "Clave1234")).thenReturn(new AuthService.Sesion(emitido,
                new PerfilResponse("mesero1", "Ana", null, List.of("MESERO"), Set.of(), null, false)));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"mesero1\",\"password\":\"Clave1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-de-prueba"))
                .andExpect(jsonPath("$.tipo").value("Bearer"));
    }

    @Test
    void loginFallidoResponde401() throws Exception {
        when(auth.autenticar(anyString(), anyString())).thenThrow(new CredencialesInvalidasException("Usuario o contraseña incorrectos"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void eventosDeAuditoriaSoloConTokenDeServicio() throws Exception {
        String cuerpo = "{\"servicio\":\"menu-service\",\"usuario\":\"admin\",\"accion\":\"CREAR\",\"entidad\":\"Plato\"}";
        mvc.perform(post("/api/auditoria/eventos").header("Authorization", token(Roles.ADMIN_PLATAFORMA))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auditoria/eventos")
                .header("Authorization", "Bearer " + tokens.emitirTokenServicio("menu-service", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isAccepted());
    }

    @Test
    void consultarAuditoriaRequierePermiso() throws Exception {
        when(auditoria.buscar(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/auditoria").header("Authorization", token(Roles.CAJERO)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").header("Authorization", token(Roles.ADMIN_PLATAFORMA)))
                .andExpect(status().isOk());
    }

    @Test
    void tokenDeServicioNoSirveParaGestionarUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header("Authorization",
                "Bearer " + tokens.emitirTokenServicio("menu-service", "x")))
                .andExpect(status().isForbidden());
    }
}
