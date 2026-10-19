package com.wokcontrol.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.inventario.dto.InventarioDtos.ConsumoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.LineaConsumo;
import com.wokcontrol.inventario.dto.InventarioDtos.ResultadoOperacion;
import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.MovimientoInventario.Tipo;
import com.wokcontrol.inventario.entity.OperacionIdempotente;
import com.wokcontrol.inventario.repository.OperacionRepository;
import com.wokcontrol.inventario.service.ExistenciasService;
import com.wokcontrol.inventario.service.OperacionInternaService;
import com.wokcontrol.inventario.service.StockInsuficienteException;

class OperacionInternaServiceTest {

    private OperacionRepository operaciones;
    private ExistenciasService existencias;
    private MongoTemplate mongo;
    private OperacionInternaService servicio;

    @BeforeEach
    void setUp() {
        operaciones = mock(OperacionRepository.class);
        existencias = mock(ExistenciasService.class);
        mongo = mock(MongoTemplate.class);
        servicio = new OperacionInternaService(operaciones, existencias, mongo, mock(AuditoriaPublisher.class));
    }

    private static Insumo insumo(String id) {
        Insumo i = new Insumo();
        i.setId(id);
        i.setNombre("Insumo " + id);
        i.setUnidad("KG");
        i.setCantidadDisponible(BigDecimal.ONE);
        return i;
    }

    @Test
    void consumoAgregaLineasDelMismoInsumoYLasDescuentaUnaVez() {
        when(existencias.disminuir(eq("arroz"), any(), eq(Tipo.CONSUMO), any(), any(), eq("PEDIDO-1")))
                .thenReturn(new ExistenciasService.Aplicado(insumo("arroz"), null));

        ResultadoOperacion r = servicio.consumir(new ConsumoRequest("PEDIDO-1", null, List.of(
                new LineaConsumo("arroz", new BigDecimal("0.2")), new LineaConsumo("arroz", new BigDecimal("0.3")))));

        assertThat(r.yaProcesada()).isFalse();
        assertThat(r.lineas()).isEqualTo(1);
        verify(existencias).disminuir(eq("arroz"), eq(new BigDecimal("0.5")), eq(Tipo.CONSUMO), any(), any(), eq("PEDIDO-1"));
    }

    @Test
    void reintentoDelMismoPedidoNoDescuentaDosVeces() {
        when(mongo.insert(any(OperacionIdempotente.class))).thenThrow(new DuplicateKeyException("dup"));
        OperacionIdempotente previa = new OperacionIdempotente("PEDIDO-1", OperacionIdempotente.Tipo.CONSUMO, "mesero");
        previa.setEstado(OperacionIdempotente.Estado.APLICADO);
        when(operaciones.findById("PEDIDO-1")).thenReturn(Optional.of(previa));

        ResultadoOperacion r = servicio.consumir(new ConsumoRequest("PEDIDO-1", null,
                List.of(new LineaConsumo("arroz", BigDecimal.ONE))));

        assertThat(r.yaProcesada()).isTrue();
        assertThat(r.estado()).isEqualTo("APLICADO");
        verify(existencias, never()).disminuir(any(), any(), any(), any(), any(), any());
    }

    @Test
    void operacionEnCursoResponde409() {
        when(mongo.insert(any(OperacionIdempotente.class))).thenThrow(new DuplicateKeyException("dup"));
        when(operaciones.findById("PEDIDO-2")).thenReturn(Optional.of(
                new OperacionIdempotente("PEDIDO-2", OperacionIdempotente.Tipo.CONSUMO, "mesero")));

        assertThatThrownBy(() -> servicio.consumir(new ConsumoRequest("PEDIDO-2", null,
                List.of(new LineaConsumo("arroz", BigDecimal.ONE))))).isInstanceOf(ConflictoException.class);
    }

    @Test
    void siUnInsumoNoAlcanzaSeCompensanLosYaDescontadosYSeLiberaLaReferencia() {
        when(existencias.disminuir(eq("arroz"), any(), any(), any(), any(), any()))
                .thenReturn(new ExistenciasService.Aplicado(insumo("arroz"), null));
        doThrow(new StockInsuficienteException("Pollo", BigDecimal.ZERO, BigDecimal.ONE, "KG"))
                .when(existencias).disminuir(eq("pollo"), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> servicio.consumir(new ConsumoRequest("PEDIDO-3", null, List.of(
                new LineaConsumo("arroz", new BigDecimal("0.2")), new LineaConsumo("pollo", BigDecimal.ONE)))))
                .isInstanceOf(StockInsuficienteException.class).hasMessageContaining("Pollo");

        verify(existencias).aumentar(eq("arroz"), eq(new BigDecimal("0.2")), eq(Tipo.REVERSION_CONSUMO), anyString(),
                any(), eq("PEDIDO-3"), isNull(), isNull(), isNull());
        verify(operaciones).deleteById("PEDIDO-3");
    }

    @Test
    void revertirConsumoInexistenteNoHaceNada() {
        when(operaciones.findById("PEDIDO-9")).thenReturn(Optional.empty());
        ResultadoOperacion r = servicio.revertirConsumo("PEDIDO-9");
        assertThat(r.estado()).isEqualTo("SIN_CONSUMO");
        verify(existencias, never()).aumentar(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
