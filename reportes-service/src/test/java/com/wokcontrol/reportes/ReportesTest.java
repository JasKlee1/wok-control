package com.wokcontrol.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.wokcontrol.common.exception.ServicioRemotoException;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.reportes.client.ServiciosClient;
import com.wokcontrol.reportes.dto.ReportesDtos.CostoPlato;
import com.wokcontrol.reportes.dto.ReportesDtos.Dashboard;
import com.wokcontrol.reportes.dto.ReportesDtos.IngredienteReceta;
import com.wokcontrol.reportes.dto.ReportesDtos.InsumoResumen;
import com.wokcontrol.reportes.dto.ReportesDtos.Plato;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenPedidos;
import com.wokcontrol.reportes.service.CostosService;
import com.wokcontrol.reportes.service.DashboardService;

class ReportesTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String rol) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("u").claim("nombre", "U")
                .claim("roles", List.of(rol)).claim("permisos", List.copyOf(Roles.SISTEMA.get(rol).permisos())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void costoDePlatoUsaRecetaPorCostoPromedio() {
        Plato p = new Plato("p1", "Arroz frito", "Arroces", new BigDecimal("25000"), true, List.of(
                new IngredienteReceta("arroz", "Arroz", "KG", new BigDecimal("0.25")),
                new IngredienteReceta("pollo", "Pollo", "KG", new BigDecimal("0.15"))));
        Map<String, InsumoResumen> insumos = Map.of(
                "arroz", new InsumoResumen("arroz", "Arroz", "KG", new BigDecimal("4000")),
                "pollo", new InsumoResumen("pollo", "Pollo", "KG", new BigDecimal("18000")));

        CostoPlato c = invocarCosto(p, insumos);

        assertThat(c.costo()).isEqualByComparingTo("3700");
        assertThat(c.margen()).isEqualByComparingTo("21300");
        assertThat(c.margenPorcentaje()).isEqualByComparingTo("85.2");
        assertThat(c.claseMargen()).isEqualTo("exito");
    }

    @Test
    void sinCostoDeAlgunInsumoNoSePresentaMargenEnganoso() {
        Plato p = new Plato("p1", "Sopa", "Sopas", new BigDecimal("15000"), true,
                List.of(new IngredienteReceta("caldo", "Caldo", "L", BigDecimal.ONE)));
        CostoPlato c = invocarCosto(p, Map.of("caldo", new InsumoResumen("caldo", "Caldo", "L", BigDecimal.ZERO)));
        assertThat(c.recetaCompleta()).isFalse();
        assertThat(c.margenPorcentaje()).isNull();
        assertThat(c.claseMargen()).isEqualTo("neutro");
    }

    @Test
    void elPanelSoloConsultaLoQueElRolPuedeVerYToleraServiciosCaidos() {
        autenticar(Roles.MESERO);
        ServiciosClient servicios = mock(ServiciosClient.class);
        when(servicios.pedidos(any())).thenThrow(new ServicioRemotoException("pedidos-service", "No fue posible comunicarse", null));

        Dashboard d = new DashboardService(servicios, "America/Bogota").construir();

        assertThat(d.ventas()).isNull();
        assertThat(d.alertas()).isNull();
        assertThat(d.compras()).isNull();
        assertThat(d.pedidos().disponible()).isFalse();
        assertThat(d.pedidos().error()).contains("No fue posible");
        verify(servicios, never()).ventas(any(), any(), any());
        verify(servicios, never()).alertas(any());
    }

    @Test
    void administradorVeTodasLasSecciones() {
        autenticar(Roles.ADMINISTRADOR);
        ServiciosClient servicios = mock(ServiciosClient.class);
        when(servicios.pedidos(any())).thenReturn(new ResumenPedidos(3, 1, 1, 0, 1, 0, 2, 6, 8, 12.5, List.of()));

        Dashboard d = new DashboardService(servicios, "America/Bogota").construir();

        assertThat(d.pedidos().datos().mesasOcupadas()).isEqualTo(2);
        assertThat(d.ventas()).isNotNull();
        assertThat(d.alertas()).isNotNull();
        assertThat(d.compras()).isNotNull();
    }

    private static CostoPlato invocarCosto(Plato p, Map<String, InsumoResumen> insumos) {
        try {
            var m = CostosService.class.getDeclaredMethod("costo", Plato.class, Map.class);
            m.setAccessible(true);
            return (CostoPlato) m.invoke(null, p, insumos);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
