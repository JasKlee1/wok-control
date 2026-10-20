package com.wokcontrol.menu.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.wokcontrol.menu.entity.Categoria;
import com.wokcontrol.menu.entity.Combo;
import com.wokcontrol.menu.entity.Imagen;
import com.wokcontrol.menu.entity.Plato;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MenuDtos {

    private MenuDtos() {
    }

    /** Alérgenos reconocidos por el sistema (para el aviso en cocina). */
    public static final List<String> ALERGENOS = List.of("MANI", "MARISCOS", "PESCADO", "GLUTEN", "SOYA", "HUEVO",
            "LACTEOS", "AJONJOLI", "FRUTOS_SECOS");

    // ---------- Categorías ----------
    public record CategoriaRequest(
            @Schema(example = "Arroces") @NotBlank(message = "El nombre es obligatorio") @Size(max = 60) String nombre,
            @Size(max = 200) String descripcion,
            @Min(value = 0, message = "El orden no puede ser negativo") @Max(999) int orden,
            boolean activa) {
    }

    public record CategoriaResponse(String id, String nombre, String descripcion, int orden, boolean activa, Imagen imagen) {
        public static CategoriaResponse de(Categoria c) {
            return new CategoriaResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.getOrden(), c.isActiva(), c.getImagen());
        }
    }

    // ---------- Platos ----------
    public record IngredienteRequest(
            @NotBlank(message = "Seleccione el insumo") String insumoId,
            @NotNull(message = "Indique la cantidad") @DecimalMin(value = "0.0001", message = "La cantidad debe ser positiva")
            @DecimalMax(value = "100000", message = "Cantidad fuera de rango") BigDecimal cantidad) {
    }

    @Schema(description = "Creación o actualización de un plato")
    public record PlatoRequest(
            @Schema(example = "Arroz frito especial") @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String nombre,
            @Size(max = 300) String descripcion,
            @NotBlank(message = "Seleccione la categoría") String categoriaId,
            @Schema(example = "28000") @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
            @DecimalMax(value = "10000000", message = "Precio fuera de rango") BigDecimal precio,
            boolean disponible,
            @Min(value = 0, message = "Mínimo 0") @Max(value = 3, message = "Máximo 3") int picanteBase,
            Set<String> alergenos,
            @Min(value = 1, message = "Mínimo 1 minuto") @Max(value = 240, message = "Máximo 240 minutos") Integer tiempoPreparacionMin,
            @Valid List<IngredienteRequest> receta) {
    }

    public record DisponibilidadRequest(boolean disponible) {
    }

    public record PrecioRequest(
            @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
            @DecimalMax(value = "10000000") BigDecimal precio) {
    }

    public record PlatoResponse(String id, String nombre, String descripcion, String categoriaId, String categoria,
            BigDecimal precio, boolean disponible, boolean activo, int picanteBase, Set<String> alergenos,
            Integer tiempoPreparacionMin, List<Plato.IngredienteReceta> receta, List<Plato.CambioPrecio> historialPrecios,
            Instant actualizadoEn, Imagen imagen) {

        public static PlatoResponse de(Plato p, String categoria) {
            return new PlatoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getCategoriaId(), categoria,
                    p.getPrecio(), p.isDisponible(), p.isActivo(), p.getPicanteBase(), p.getAlergenos(),
                    p.getTiempoPreparacionMin(), p.getReceta(), p.getHistorialPrecios(), p.getActualizadoEn(), p.getImagen());
        }
    }

    // ---------- Combos ----------
    public record ItemComboRequest(
            @NotBlank(message = "Seleccione el plato") String platoId,
            @Min(value = 1, message = "La cantidad mínima es 1") @Max(value = 20, message = "Máximo 20") int cantidad) {
    }

    public record ComboRequest(
            @Schema(example = "Combo familiar") @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String nombre,
            @Size(max = 300) String descripcion,
            @NotEmpty(message = "Agregue al menos un plato") @Valid List<ItemComboRequest> items,
            @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
            @DecimalMax(value = "10000000") BigDecimal precio,
            boolean disponible) {
    }

    public record ComboResponse(String id, String nombre, String descripcion, List<Combo.ItemCombo> items,
            BigDecimal precio, BigDecimal precioPlatosSueltos, boolean disponible, boolean disponibleEfectivo,
            boolean activo, Imagen imagen) {
    }

    // ---------- Carta y cotización ----------
    public enum TipoProducto { PLATO, COMBO }

    public record ItemCotizar(@NotNull TipoProducto tipo, @NotBlank String id,
            @Min(value = 1, message = "La cantidad mínima es 1") @Max(value = 50, message = "Máximo 50 unidades") int cantidad) {
    }

    @Schema(description = "Productos a cotizar para un pedido")
    public record CotizacionRequest(@NotEmpty(message = "Incluya al menos un producto") @Valid List<ItemCotizar> items) {
    }

    public record ConsumoInsumo(String insumoId, String nombreInsumo, String unidad, BigDecimal cantidadPorUnidad) {
    }

    @Schema(description = "Precio vigente, disponibilidad y receta (insumos por unidad) de cada producto")
    public record ItemCotizado(TipoProducto tipo, String id, String nombre, BigDecimal precioUnitario, boolean disponible,
            String motivoNoDisponible, int picanteBase, Set<String> alergenos, List<ConsumoInsumo> receta) {
    }

    public record CotizacionResponse(List<ItemCotizado> items, boolean todosDisponibles) {
    }

    /** imagenId: foto del producto o, si no tiene, la de su categoría (null si ninguna). */
    public record ProductoCarta(TipoProducto tipo, String id, String nombre, String descripcion, BigDecimal precio,
            int picanteBase, Set<String> alergenos, String imagenId) {
    }

    public record SeccionCarta(String categoriaId, String categoria, List<ProductoCarta> productos) {
    }
}
