package com.wokcontrol.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.config.MongoAuditoriaConfig;
import com.wokcontrol.inventario.dto.InventarioDtos.ConsumoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.LineaConsumo;
import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.MovimientoInventario;
import com.wokcontrol.inventario.entity.OperacionIdempotente;
import com.wokcontrol.inventario.entity.MovimientoInventario.Tipo;
import com.wokcontrol.inventario.repository.InsumoRepository;
import com.wokcontrol.inventario.repository.MovimientoRepository;
import com.wokcontrol.inventario.service.ExistenciasService;
import com.wokcontrol.inventario.service.OperacionInternaService;
import com.wokcontrol.inventario.service.StockInsuficienteException;

/**
 * Prueba contra un MongoDB real (Atlas) en una base de datos EXCLUSIVA de pruebas
 * (wok_test_inventario). Solo se ejecuta si existe la variable WOK_TEST_MONGODB_URI; nunca toca
 * las bases de datos del restaurante.
 */
@DataMongoTest
@Import({ ExistenciasService.class, OperacionInternaService.class, MongoAuditoriaConfig.class })
@EnabledIfEnvironmentVariable(named = "WOK_TEST_MONGODB_URI", matches = ".+")
class ExistenciasMongoIntegrationTest {

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry r) {
        r.add("spring.mongodb.uri", () -> System.getenv("WOK_TEST_MONGODB_URI"));
        r.add("spring.mongodb.database", () -> "wok_test_inventario");
        r.add("spring.data.mongodb.representation.big-decimal", () -> "decimal128");
        r.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    MongoTemplate mongo;
    @Autowired
    InsumoRepository insumos;
    @Autowired
    MovimientoRepository movimientos;
    @Autowired
    ExistenciasService existencias;
    @Autowired
    OperacionInternaService operaciones;
    @MockitoBean
    AuditoriaPublisher auditoria;

    private Insumo pollo;

    @BeforeEach
    void limpiarBaseDePruebas() {
        assertThat(mongo.getDb().getName()).isEqualTo("wok_test_inventario");
        mongo.dropCollection(Insumo.class);
        mongo.dropCollection(MovimientoInventario.class);
        mongo.dropCollection(OperacionIdempotente.class);
        Insumo i = new Insumo();
        i.setNombre("Pollo prueba");
        i.setCategoria("PROTEINAS");
        i.setUnidad("KG");
        i.setStockMinimo(BigDecimal.ONE);
        pollo = insumos.save(i);
        existencias.aumentar(pollo.getId(), new BigDecimal("10"), Tipo.ENTRADA, "Carga inicial", "test", "MANUAL",
                new BigDecimal("12000"), null, null);
    }

    @Test
    void salidasConcurrentesNuncaDejanExistenciasNegativas() throws Exception {
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger exitosas = new AtomicInteger();
        AtomicInteger rechazadas = new AtomicInteger();
        for (int k = 0; k < hilos; k++) {
            pool.submit(() -> {
                inicio.await();
                try {
                    existencias.disminuir(pollo.getId(), BigDecimal.ONE, Tipo.SALIDA, "Prueba concurrente", "test", "MANUAL");
                    exitosas.incrementAndGet();
                } catch (StockInsuficienteException ex) {
                    rechazadas.incrementAndGet();
                }
                return null;
            });
        }
        inicio.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        Insumo fin = insumos.findById(pollo.getId()).orElseThrow();
        assertThat(exitosas.get()).isEqualTo(10);
        assertThat(rechazadas.get()).isEqualTo(10);
        assertThat(fin.getCantidadDisponible()).isEqualByComparingTo("0");
        // 1 entrada + 10 salidas: cada cambio tiene su movimiento
        assertThat(movimientos.count()).isEqualTo(11);
    }

    @Test
    void consumoReintentadoSeAplicaUnaSolaVezYSuReversionTambien() {
        ConsumoRequest req = new ConsumoRequest("PEDIDO-TEST-1", "Prueba",
                List.of(new LineaConsumo(pollo.getId(), new BigDecimal("2.5"))));

        assertThat(operaciones.consumir(req).yaProcesada()).isFalse();
        assertThat(operaciones.consumir(req).yaProcesada()).isTrue();
        assertThat(insumos.findById(pollo.getId()).orElseThrow().getCantidadDisponible()).isEqualByComparingTo("7.5");

        assertThat(operaciones.revertirConsumo("PEDIDO-TEST-1").yaProcesada()).isFalse();
        assertThat(operaciones.revertirConsumo("PEDIDO-TEST-1").yaProcesada()).isTrue();
        assertThat(insumos.findById(pollo.getId()).orElseThrow().getCantidadDisponible()).isEqualByComparingTo("10");
    }

    @Test
    void costoPromedioPonderadoSeRecalculaEnEntradas() {
        existencias.aumentar(pollo.getId(), new BigDecimal("10"), Tipo.ENTRADA, "Compra", "test", "MANUAL",
                new BigDecimal("14000"), null, null);
        Insumo fin = insumos.findById(pollo.getId()).orElseThrow();
        assertThat(fin.getCantidadDisponible()).isEqualByComparingTo("20");
        assertThat(fin.getCostoUnitario()).isEqualByComparingTo("13000");
    }

    @Test
    void ajusteRegistraLaDiferenciaComoMovimientoAuditado() {
        MovimientoInventario m = existencias.ajustar(pollo.getId(), new BigDecimal("8.5"), "Conteo semanal", "test").movimiento();
        assertThat(m.getTipo()).isEqualTo(Tipo.AJUSTE_NEGATIVO);
        assertThat(m.getCantidad()).isEqualByComparingTo("1.5");
        assertThat(m.getSaldoAnterior()).isEqualByComparingTo("10");
        assertThat(m.getSaldoNuevo()).isEqualByComparingTo("8.5");
    }
}
