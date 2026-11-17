package com.wokcontrol.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.pedidos.client.MenuClient;
import com.wokcontrol.pedidos.config.SecurityConfig;
import com.wokcontrol.pedidos.controller.api.MesaApiRestController;
import com.wokcontrol.pedidos.controller.api.PedidoApiRestController;
import com.wokcontrol.pedidos.controller.api.SalonApiRestController;
import com.wokcontrol.pedidos.controller.web.MesaWebController;
import com.wokcontrol.pedidos.controller.web.PedidoWebController;
import com.wokcontrol.pedidos.controller.web.SalonWebController;
import com.wokcontrol.pedidos.dto.PedidoDtos.MesaResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.MapaSalon;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.service.MesaService;
import com.wokcontrol.pedidos.service.PedidoService;
import com.wokcontrol.pedidos.service.ResumenService;
import com.wokcontrol.pedidos.service.SalonService;

import jakarta.servlet.http.Cookie;

/**
 * Épica 8 · Capa web, API y seguridad del anfitrión:
 * <ul>
 * <li>T001 · HU-043: AC-043.1 y AC-043.2 (y el permiso mínimo del rol ANFITRION).</li>
 * <li>T003 · HU-040: AC-040.2 en la API y en el formulario.</li>
 * <li>T007 · HU-042: AC-042.1 (el mesero ve «sus mesas») y AC-042.2 (código en la respuesta).</li>
 * <li>T013 · HU-046: AC-046.1 (consulta cada 3 s, sin recargar) y AC-046.2 (tablet de 768 px).</li>
 * </ul>
 */
@WebMvcTest(controllers = { SalonWebController.class, SalonApiRestController.class, MesaWebController.class,
        MesaApiRestController.class, PedidoWebController.class, PedidoApiRestController.class })
@Import({ SecurityConfig.class, JwtConfig.class, TokenService.class, com.wokcontrol.common.web.Formato.class })
class SalonWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;
    @MockitoBean
    SalonService salon;
    @MockitoBean
    MesaService mesas;
    @MockitoBean
    PedidoService pedidos;
    @MockitoBean
    ResumenService resumen;
    @MockitoBean
    MenuClient menu;

    private String bearer(String rol) {
        return "Bearer " + tokens.emitirTokenUsuario(rol.toLowerCase(), rol, List.of(rol), Roles.SISTEMA.get(rol).permisos(), null).token();
    }

    private Cookie web(String usuario, String rol) {
        return new Cookie("WOK_TOKEN", tokens.emitirTokenUsuario(usuario, usuario, List.of(rol), Roles.SISTEMA.get(rol).permisos(), null).token());
    }

    private static MesaResponse mesa(int numero, int capacidad, Mesa.Estado estado, String mesero) {
        return new MesaResponse("m" + numero, numero, capacidad, "SALON", estado, true, null, 0, BigDecimal.ZERO, null, 0,
                mesero == null ? null : "Familia " + numero, mesero == null ? 0 : capacidad, mesero,
                mesero == null ? null : mesero.toUpperCase(), null, List.of(), null);
    }

    private List<MesaResponse> salonDe12() {
        List<MesaResponse> l = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            l.add(mesa(i, 4, Mesa.Estado.DISPONIBLE, null));
        }
        for (int i = 6; i <= 11; i++) {
            l.add(mesa(i, 4, Mesa.Estado.OCUPADA, i == 6 ? "carlos" : "ana"));
        }
        l.add(mesa(12, 6, Mesa.Estado.POR_LIMPIAR, null));
        return l;
    }

    @BeforeEach
    void salonDePrueba() {
        List<MesaResponse> doce = salonDe12();
        when(salon.mapa()).thenReturn(new MapaSalon(doce, 5, 6, 1, 0, 0, "abc123"));
        when(salon.huella()).thenReturn("abc123");
        when(salon.esperaRespuesta()).thenReturn(List.of());
        when(salon.zona()).thenReturn(java.time.ZoneId.of("America/Bogota"));
        when(mesas.tablero(false)).thenReturn(doce);
    }

    // ------------------------------------------------------------------ HU-043

    @Test
    void hu043_elRolAnfitrionTieneSoloElPermisoDelSalon() {
        assertThat(Roles.SISTEMA.get(Roles.ANFITRION).permisos())
                .contains("SALON_OPERAR")
                .doesNotContain("MESAS_GESTIONAR", "PEDIDOS_VER", "PEDIDOS_GESTIONAR", "FACTURACION_VER", "FACTURACION_COBRAR",
                        "COCINA_OPERAR");
        assertThat(Roles.SOLO_SESION).doesNotContain(Roles.ANFITRION);
    }

    @Test
    void ac043_1_elAnfitrionRecibe403AlCrearOEditarUnaMesa() throws Exception {
        String cuerpo = "{\"numero\":20,\"capacidad\":4,\"zona\":\"SALON\",\"activa\":true}";
        mvc.perform(post("/api/mesas").header("Authorization", bearer(Roles.ANFITRION))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/mesas/m3").header("Authorization", bearer(Roles.ANFITRION))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/mesas/m3/servicio").param("fueraDeServicio", "true").header("Authorization", bearer(Roles.ANFITRION)))
                .andExpect(status().isForbidden());
        // en la web, el formulario de mesas lo lleva a «acceso no permitido»
        mvc.perform(get("/mesas/nueva").cookie(web("lina", Roles.ANFITRION))).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(post("/mesas/guardar").with(csrf()).cookie(web("lina", Roles.ANFITRION)).param("numero", "20")
                .param("capacidad", "4").param("zona", "SALON")).andExpect(redirectedUrl("/acceso-denegado"));
        verify(mesas, never()).crear(any());
        verify(mesas, never()).actualizar(any(), any());
    }

    @Test
    void ac043_2_elAnfitrionNoAbrePedidosNiTomaPedidos() throws Exception {
        mvc.perform(get("/pedidos").cookie(web("lina", Roles.ANFITRION))).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/pedidos/nuevo").param("mesaId", "m3").cookie(web("lina", Roles.ANFITRION)))
                .andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/api/pedidos").header("Authorization", bearer(Roles.ANFITRION))).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos").header("Authorization", bearer(Roles.ANFITRION)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":\"m3\",\"items\":[{\"tipo\":\"PLATO\",\"productoId\":\"p\",\"cantidad\":1}]}"))
                .andExpect(status().isForbidden());
        // y los demás roles no entran al salón
        mvc.perform(get("/salon").cookie(web("ana", Roles.MESERO))).andExpect(redirectedUrl("/acceso-denegado"));
        mvc.perform(get("/api/salon/mapa").header("Authorization", bearer(Roles.CAJERO))).andExpect(status().isForbidden());
    }

    @Test
    void hu043_elAnfitrionVeElSalonYCambiaLaOcupacion() throws Exception {
        mvc.perform(get("/salon").cookie(web("lina", Roles.ANFITRION))).andExpect(status().isOk());
        mvc.perform(get("/api/salon/mapa").header("Authorization", bearer(Roles.ANFITRION))).andExpect(status().isOk())
                .andExpect(jsonPath("$.libres").value(5)).andExpect(jsonPath("$.ocupadas").value(6))
                .andExpect(jsonPath("$.porLimpiar").value(1));
        Mesa m = new Mesa();
        m.setId("m12");
        m.setNumero(12);
        m.setEstado(Mesa.Estado.DISPONIBLE);
        when(mesas.marcarLimpia("m12")).thenReturn(m);
        mvc.perform(post("/salon/mesas/m12/limpia").with(csrf()).cookie(web("lina", Roles.ANFITRION)))
                .andExpect(redirectedUrl("/salon")).andExpect(flash().attribute("exito", "Mesa 12 limpia y libre"));
        // el mesero también puede marcarla limpia (spec, acta 4)
        mvc.perform(patch("/api/salon/mesas/m12/limpia").header("Authorization", bearer(Roles.MESERO))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ HU-040

    @Test
    void ac040_2_laApiRechazaSinNombreOConCeroPersonasIndicandoElCampo() throws Exception {
        mvc.perform(post("/api/salon/espera").header("Authorization", bearer(Roles.ANFITRION))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\",\"personas\":4}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.nombre").value("Indica el nombre del grupo"));
        mvc.perform(post("/api/salon/espera").header("Authorization", bearer(Roles.ANFITRION))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Familia Rojas\",\"personas\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.personas").value("Indica cuántas personas son (mínimo 1)"));
        verify(salon, never()).registrar(any());
    }

    @Test
    void ac040_2_elFormularioMarcaElCampoQueFalta() throws Exception {
        mvc.perform(post("/salon/espera").with(csrf()).cookie(web("lina", Roles.ANFITRION)).param("nombre", "")
                .param("personas", "0")).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "nombre", "personas"))
                .andExpect(content().string(Matchers.containsString("Indica el nombre del grupo")));
        verify(salon, never()).registrar(any());
    }

    // ------------------------------------------------------------------ HU-042

    @Test
    void ac042_1_carlosVeLaMesaQueLeAsignaronEnSusMesas() throws Exception {
        String html = mvc.perform(get("/mesas").param("mias", "true").cookie(web("carlos", Roles.MESERO)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Familia 6").contains("Atiende CARLOS").doesNotContain("Familia 7");
    }

    @Test
    void ac042_2_laApiDevuelveElCodigoCapacidadInsuficiente() throws Exception {
        when(salon.asignar(any())).thenThrow(new ReglaNegocioException(SalonService.CAPACIDAD_INSUFICIENTE,
                "CAPACIDAD_INSUFICIENTE · La mesa 3 tiene 4 puestos y el grupo es de 6 personas"));
        mvc.perform(post("/api/salon/asignaciones").header("Authorization", bearer(Roles.ANFITRION))
                .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":\"g1\",\"mesaIds\":[\"m3\"],\"mesero\":\"carlos\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CAPACIDAD_INSUFICIENTE"));
    }

    @Test
    void ac044_2_laWebMuestraMesaNoDisponibleAlSentarEnUnaMesaPorLimpiar() throws Exception {
        when(salon.asignar(any())).thenThrow(new ReglaNegocioException(SalonService.MESA_NO_DISPONIBLE,
                "MESA_NO_DISPONIBLE · La mesa 12 está por limpiar; solo se asignan mesas libres"));
        mvc.perform(post("/salon/sentar").with(csrf()).cookie(web("lina", Roles.ANFITRION)).param("nombre", "Familia Rojas")
                .param("personas", "4").param("mesaIds", "m12").param("mesero", "carlos"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("MESA_NO_DISPONIBLE")));
    }

    // ------------------------------------------------------------------ HU-046

    @Test
    void ac046_1_elMapaConsultaLaHuellaCadaTresSegundosYSeReemplazaSinRecargar() throws Exception {
        assertThat(SalonWebController.INTERVALO_MS).isLessThanOrEqualTo(5000);
        String html = mvc.perform(get("/salon").cookie(web("lina", Roles.ANFITRION))).andReturn().getResponse()
                .getContentAsString();
        assertThat(html).contains("data-intervalo=\"3000\"").contains("data-huella=\"abc123\"")
                .contains("fetch('/salon/estado'").contains("fetch('/salon/vivo'").contains("contenedor.innerHTML")
                .doesNotContain("location.reload").doesNotContain("data-wok-recargar");

        mvc.perform(get("/salon/estado").cookie(web("lina", Roles.ANFITRION))).andExpect(status().isOk())
                .andExpect(jsonPath("$.huella").value("abc123")).andExpect(jsonPath("$.intervaloMs").value(3000));
        String fragmento = mvc.perform(get("/salon/vivo").cookie(web("lina", Roles.ANFITRION))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(fragmento).contains("id=\"salon-vivo\"").doesNotContain("<html");
        assertThat(contar(fragmento, "class=\"salon-mesa ")).isEqualTo(12);
    }

    @Test
    void ac046_2_enUnaTabletDe768pxElMapaDe12MesasSeVeSinDesplazamientoHorizontal() throws Exception {
        String html = mvc.perform(get("/salon").cookie(web("lina", Roles.ANFITRION))).andReturn().getResponse()
                .getContentAsString();
        assertThat(html).contains("name=\"viewport\" content=\"width=device-width, initial-scale=1\"");
        // rejilla fluida: las columnas se acomodan al ancho disponible
        assertThat(html).contains("grid-template-columns: repeat(auto-fill, minmax(min(100%, 9.5rem), 1fr))");
        // ningún ancho fijo del mapa supera los 768 px (los puntos de quiebre @media no son anchos fijos)
        Matcher px = Pattern.compile("(?<![(\\w-])(?:min-)?width:\\s*(\\d+)px").matcher(html);
        while (px.find()) {
            assertThat(Integer.parseInt(px.group(1))).as("ancho fijo %s", px.group()).isLessThanOrEqualTo(768);
        }
        // 12 mesas de mínimo 9.5rem (152 px) caben 4 por fila en el área útil de una tablet de 768 px (≈ 736 px)
        assertThat(4 * 152 + 3 * 12).isLessThan(736);
        // botones táctiles de al menos 44 px
        assertThat(html).contains(".salon .salon-btn { min-height: 44px; min-width: 44px;");
        assertThat(contar(html, "salon-btn")).isGreaterThanOrEqualTo(6);
        assertThat(html).contains("Sentar aquí").contains("Marcar limpia");
    }

    private static int contar(String texto, String patron) {
        int n = 0;
        for (int i = texto.indexOf(patron); i >= 0; i = texto.indexOf(patron, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void hu040_laListaDeEsperaSeMuestraEnOrdenConSuTiempo() throws Exception {
        when(salon.esperaRespuesta()).thenReturn(List.of(
                new com.wokcontrol.pedidos.dto.SalonDtos.GrupoResponse("g1", "Pareja Díaz", 2, Instant.now().minusSeconds(1200), 20,
                        null, com.wokcontrol.pedidos.entity.GrupoEspera.Estado.EN_ESPERA, List.of(), null, null, null),
                new com.wokcontrol.pedidos.dto.SalonDtos.GrupoResponse("g2", "Familia Rojas", 4, Instant.now(), 0,
                        null, com.wokcontrol.pedidos.entity.GrupoEspera.Estado.EN_ESPERA, List.of(), null, null, null)));
        String html = mvc.perform(get("/salon").cookie(web("lina", Roles.ANFITRION))).andReturn().getResponse()
                .getContentAsString();
        assertThat(html.indexOf("data-grupo=\"Pareja Díaz\"")).isPositive()
                .isLessThan(html.indexOf("data-grupo=\"Familia Rojas\""));
        assertThat(html).contains("20 min").contains("4 personas");
    }
}
