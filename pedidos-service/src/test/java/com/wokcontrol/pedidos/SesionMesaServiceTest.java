package com.wokcontrol.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.security.JwtConfig;
import com.wokcontrol.common.security.JwtProperties;
import com.wokcontrol.common.security.Permisos;
import com.wokcontrol.common.security.Roles;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.repository.MesaRepository;
import com.wokcontrol.pedidos.repository.SesionMesaRepository;
import com.wokcontrol.pedidos.service.SesionMesaService;
import com.wokcontrol.pedidos.service.SesionMesaService.MesaNoDisponibleException;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionAbierta;
import com.wokcontrol.pedidos.service.SesionMesaService.SesionVencidaException;

/**
 * Épica 7 · T001 (pruebas de HU-036) y T003 (pruebas de HU-033). Se escribieron antes que
 * {@link SesionMesaService}.
 */
class SesionMesaServiceTest {

    private MesaRepository mesas;
    private SesionMesaRepository sesiones;
    private JwtDecoder decoder;
    private SesionMesaService servicio;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties("clave-de-pruebas-unitarias-wok-control-no-usar-en-produccion",
                null, null, null, null, null);
        JwtConfig cfg = new JwtConfig();
        SecretKey key = cfg.wokJwtKey(props);
        decoder = cfg.jwtDecoder(key, props);
        TokenService tokens = new TokenService(cfg.jwtEncoder(key), props);
        mesas = mock(MesaRepository.class);
        sesiones = mock(SesionMesaRepository.class);
        when(sesiones.save(any(SesionMesa.class))).thenAnswer(i -> {
            SesionMesa s = i.getArgument(0);
            s.setId("s1");
            return s;
        });
        servicio = new SesionMesaService(mesas, sesiones, mock(MongoTemplate.class), tokens, mock(AuditoriaPublisher.class));
    }

    private Mesa mesa(String id, int numero, boolean activa, Mesa.Estado estado) {
        Mesa m = new Mesa();
        m.setId(id);
        m.setNumero(numero);
        m.setActiva(activa);
        m.setEstado(estado);
        m.setCodigoQr("qr-" + numero);
        when(mesas.findByCodigoQr("qr-" + numero)).thenReturn(Optional.of(m));
        return m;
    }

    private static SesionMesa sesion(String id, String mesaId, SesionMesa.Estado estado) {
        SesionMesa s = new SesionMesa();
        s.setId(id);
        s.setMesaId(mesaId);
        s.setMesaNumero(5);
        s.setEstado(estado);
        return s;
    }

    @Test
    void qrDeMesaActivaAbreSesionLimitadaASuMesa() {
        mesa("m5", 5, true, Mesa.Estado.DISPONIBLE);

        SesionAbierta s = servicio.abrir("qr-5");

        assertThat(s.mesaNumero()).isEqualTo(5);
        Jwt jwt = decoder.decode(s.token());
        assertThat(jwt.getClaimAsString(TokenService.CLAIM_MESA)).isEqualTo("m5");
        assertThat(jwt.getClaimAsString(TokenService.CLAIM_SESION)).isEqualTo("s1");
        assertThat(jwt.getClaimAsStringList(TokenService.CLAIM_ROLES)).containsExactly(Roles.CLIENTE);
        assertThat(jwt.getClaimAsStringList(TokenService.CLAIM_PERMISOS)).containsExactly(Permisos.CARTA_CLIENTE);
    }

    @Test
    void ac033_2_qrInexistenteNoAbreSesion() {
        when(mesas.findByCodigoQr("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.abrir("no-existe"))
                .isInstanceOf(MesaNoDisponibleException.class)
                .hasMessage("Mesa no disponible, pide ayuda al personal");
        verify(sesiones, never()).save(any());
    }

    @Test
    void ac033_2_mesaInactivaNoAbreSesion() {
        mesa("m9", 9, false, Mesa.Estado.DISPONIBLE);

        assertThatThrownBy(() -> servicio.abrir("qr-9")).isInstanceOf(MesaNoDisponibleException.class);
        verify(sesiones, never()).save(any());
    }

    @Test
    void ac033_2_mesaFueraDeServicioNoAbreSesion() {
        mesa("m8", 8, true, Mesa.Estado.FUERA_DE_SERVICIO);

        assertThatThrownBy(() -> servicio.abrir("qr-8")).isInstanceOf(MesaNoDisponibleException.class);
        verify(sesiones, never()).save(any());
    }

    @Test
    void ac036_2_sesionCerradaAlCobrarYaNoSirve() {
        when(sesiones.findById("s1")).thenReturn(Optional.of(sesion("s1", "m5", SesionMesa.Estado.CERRADA)));

        assertThatThrownBy(() -> servicio.validar("s1", "m5")).isInstanceOf(SesionVencidaException.class);
    }

    @Test
    void ac036_1_sesionNoSirveParaOtraMesa() {
        when(sesiones.findById("s1")).thenReturn(Optional.of(sesion("s1", "m5", SesionMesa.Estado.ACTIVA)));

        assertThatThrownBy(() -> servicio.validar("s1", "m7")).isInstanceOf(SesionVencidaException.class);
    }

    @Test
    void sesionActivaDeSuMesaEsValida() {
        when(sesiones.findById("s1")).thenReturn(Optional.of(sesion("s1", "m5", SesionMesa.Estado.ACTIVA)));

        assertThat(servicio.validar("s1", "m5").getMesaNumero()).isEqualTo(5);
    }

    @Test
    void codigoQrVacioNoAbreSesion() {
        for (String codigo : List.of("", "   ")) {
            assertThatThrownBy(() -> servicio.abrir(codigo)).isInstanceOf(MesaNoDisponibleException.class);
        }
        verify(sesiones, never()).save(any());
    }
}
