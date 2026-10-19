package com.wokcontrol.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.menu.client.InventarioClient;
import com.wokcontrol.menu.client.InventarioClient.InsumoResumen;
import com.wokcontrol.menu.dto.MenuDtos.CotizacionRequest;
import com.wokcontrol.menu.dto.MenuDtos.CotizacionResponse;
import com.wokcontrol.menu.dto.MenuDtos.IngredienteRequest;
import com.wokcontrol.menu.dto.MenuDtos.ItemCotizar;
import com.wokcontrol.menu.dto.MenuDtos.PlatoRequest;
import com.wokcontrol.menu.dto.MenuDtos.TipoProducto;
import com.wokcontrol.menu.entity.Categoria;
import com.wokcontrol.menu.entity.Combo;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.repository.CategoriaRepository;
import com.wokcontrol.menu.repository.ComboRepository;
import com.wokcontrol.menu.repository.PlatoRepository;
import com.wokcontrol.menu.service.CartaService;
import com.wokcontrol.menu.service.CategoriaService;
import com.wokcontrol.menu.service.ComboService;
import com.wokcontrol.menu.service.PlatoService;

class MenuServiciosTest {

    private PlatoRepository platos;
    private ComboRepository combos;
    private CategoriaRepository categoriasRepo;
    private InventarioClient inventario;
    private PlatoService platoService;
    private CartaService carta;

    @BeforeEach
    void setUp() {
        platos = mock(PlatoRepository.class);
        combos = mock(ComboRepository.class);
        categoriasRepo = mock(CategoriaRepository.class);
        inventario = mock(InventarioClient.class);
        AuditoriaPublisher aud = mock(AuditoriaPublisher.class);
        CategoriaService categorias = new CategoriaService(categoriasRepo, platos, aud);
        ComboService comboService = new ComboService(combos, platos, aud);
        platoService = new PlatoService(platos, categorias, inventario, mock(MongoTemplate.class), aud);
        carta = new CartaService(platos, combos, categorias, comboService);
        when(platos.save(any(Plato.class))).thenAnswer(i -> i.getArgument(0));

        Categoria c = new Categoria();
        c.setId("cat1");
        c.setNombre("Arroces");
        c.setActiva(true);
        when(categoriasRepo.findById("cat1")).thenReturn(Optional.of(c));
    }

    private static Plato plato(String id, String precio, boolean disponible, Plato.IngredienteReceta... receta) {
        Plato p = new Plato();
        p.setId(id);
        p.setNombre("Plato " + id);
        p.setPrecio(new BigDecimal(precio));
        p.setDisponible(disponible);
        p.setActivo(true);
        p.setReceta(List.of(receta));
        return p;
    }

    @Test
    void cotizacionDeComboSumaLasRecetasDeSusPlatos() {
        Plato arroz = plato("p1", "20000", true, new Plato.IngredienteReceta("arroz", "Arroz", "KG", new BigDecimal("0.2")));
        Plato lumpia = plato("p2", "8000", true, new Plato.IngredienteReceta("arroz", "Arroz", "KG", new BigDecimal("0.05")),
                new Plato.IngredienteReceta("aceite", "Aceite", "L", new BigDecimal("0.02")));
        Combo combo = new Combo();
        combo.setId("c1");
        combo.setNombre("Combo pareja");
        combo.setPrecio(new BigDecimal("50000"));
        combo.setDisponible(true);
        combo.setActivo(true);
        combo.setItems(List.of(new Combo.ItemCombo("p1", "Arroz", 2), new Combo.ItemCombo("p2", "Lumpia", 1)));
        when(combos.findById("c1")).thenReturn(Optional.of(combo));
        when(platos.findAllById(anyIterable())).thenReturn(List.of(arroz, lumpia));

        CotizacionResponse r = carta.cotizar(new CotizacionRequest(List.of(new ItemCotizar(TipoProducto.COMBO, "c1", 1))));

        assertThat(r.todosDisponibles()).isTrue();
        assertThat(r.items().get(0).precioUnitario()).isEqualByComparingTo("50000");
        var receta = r.items().get(0).receta();
        assertThat(receta).hasSize(2);
        assertThat(receta.stream().filter(x -> x.insumoId().equals("arroz")).findFirst().get().cantidadPorUnidad())
                .isEqualByComparingTo("0.45");
    }

    @Test
    void comboNoDisponibleSiUnPlatoEstaAgotado() {
        Plato agotado = plato("p1", "20000", false);
        Combo combo = new Combo();
        combo.setId("c1");
        combo.setPrecio(BigDecimal.TEN);
        combo.setDisponible(true);
        combo.setActivo(true);
        combo.setItems(List.of(new Combo.ItemCombo("p1", "Arroz", 1)));
        when(combos.findById("c1")).thenReturn(Optional.of(combo));
        when(platos.findAllById(anyIterable())).thenReturn(List.of(agotado));

        CotizacionResponse r = carta.cotizar(new CotizacionRequest(List.of(new ItemCotizar(TipoProducto.COMBO, "c1", 1))));

        assertThat(r.todosDisponibles()).isFalse();
        assertThat(r.items().get(0).motivoNoDisponible()).contains("Arroz");
    }

    @Test
    void cambioDePrecioQuedaEnHistorial() {
        Plato p = plato("p1", "20000", true);
        when(platos.findById("p1")).thenReturn(Optional.of(p));

        Plato r = platoService.cambiarPrecio("p1", new BigDecimal("22000"));

        assertThat(r.getPrecio()).isEqualByComparingTo("22000");
        assertThat(r.getHistorialPrecios()).hasSize(1);
        assertThat(r.getHistorialPrecios().get(0).anterior()).isEqualByComparingTo("20000");
    }

    @Test
    void recetaConInsumoInexistenteEsRechazada() {
        when(inventario.insumosActivos()).thenReturn(List.of(
                new InsumoResumen("arroz", "Arroz", "KG", "GRANOS", BigDecimal.ONE, BigDecimal.TEN, true)));
        PlatoRequest req = new PlatoRequest("Chop suey", null, "cat1", new BigDecimal("25000"), true, 1, Set.of(), 15,
                List.of(new IngredienteRequest("no-existe", new BigDecimal("0.1"))));

        assertThatThrownBy(() -> platoService.crear(req)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no existe");
        verify(platos, never()).save(any());
    }

    @Test
    void recetaValidaCopiaNombreYUnidadDelInsumo() {
        when(inventario.insumosActivos()).thenReturn(List.of(
                new InsumoResumen("arroz", "Arroz jazmín", "KG", "GRANOS", BigDecimal.ONE, BigDecimal.TEN, true)));
        PlatoRequest req = new PlatoRequest("Arroz frito", null, "cat1", new BigDecimal("25000"), true, 0,
                Set.of("SOYA"), 12, List.of(new IngredienteRequest("arroz", new BigDecimal("0.25"))));

        Plato p = platoService.crear(req);

        assertThat(p.getReceta()).singleElement().satisfies(r -> {
            assertThat(r.nombreInsumo()).isEqualTo("Arroz jazmín");
            assertThat(r.unidad()).isEqualTo("KG");
        });
        assertThat(p.getHistorialPrecios()).hasSize(1);
    }

    @Test
    void alergenoDesconocidoEsRechazado() {
        PlatoRequest req = new PlatoRequest("Sopa", null, "cat1", new BigDecimal("15000"), true, 0,
                Set.of("CHOCOLATE"), 10, List.of());
        assertThatThrownBy(() -> platoService.crear(req)).isInstanceOf(ReglaNegocioException.class);
    }
}
