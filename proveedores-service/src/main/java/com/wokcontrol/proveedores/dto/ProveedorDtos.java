package com.wokcontrol.proveedores.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.wokcontrol.proveedores.entity.OrdenCompra;
import com.wokcontrol.proveedores.entity.Proveedor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProveedorDtos {

    private ProveedorDtos() {
    }

    public static final String TELEFONO = "^[0-9+ ()-]{7,20}$";

    @Schema(description = "Registro o edición de proveedor")
    public record ProveedorRequest(
            @Schema(example = "900123456-7") @NotBlank(message = "El NIT o identificación es obligatorio")
            @Pattern(regexp = "^[0-9]{5,15}(-[0-9])?$", message = "NIT inválido (solo números y dígito de verificación opcional)") String nit,
            @NotBlank(message = "La razón social es obligatoria") @Size(max = 120) String razonSocial,
            @NotBlank(message = "El nombre comercial es obligatorio") @Size(max = 80) String nombreComercial,
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = TELEFONO, message = "Teléfono inválido") String telefono,
            @Email(message = "Correo inválido") @Size(max = 120) String email,
            @Size(max = 150) String direccion,
            @Size(max = 60) String ciudad,
            @Size(max = 80) String contactoNombre,
            @Size(max = 60) String contactoCargo,
            @Pattern(regexp = "^$|" + TELEFONO, message = "Teléfono inválido") String contactoTelefono,
            @Email(message = "Correo inválido") String contactoEmail,
            Set<String> categorias,
            Set<String> insumoIds,
            @Min(value = 0, message = "No puede ser negativo") @Max(365) Integer plazoPagoDias,
            @DecimalMin(value = "0", message = "No puede ser negativo") BigDecimal pedidoMinimo,
            @Min(value = 0, message = "No puede ser negativo") @Max(90) Integer tiempoEntregaDias,
            @Size(max = 40) String formaPago,
            @Size(max = 250) String observacionesComerciales) {
    }

    @Schema(description = "Datos de contacto que el propio proveedor puede actualizar desde su portal")
    public record PerfilProveedorRequest(
            @NotBlank(message = "El teléfono es obligatorio") @Pattern(regexp = TELEFONO, message = "Teléfono inválido") String telefono,
            @Email(message = "Correo inválido") @Size(max = 120) String email,
            @Size(max = 150) String direccion,
            @Size(max = 80) String contactoNombre,
            @Pattern(regexp = "^$|" + TELEFONO, message = "Teléfono inválido") String contactoTelefono,
            @Email(message = "Correo inválido") String contactoEmail) {
    }

    public record ProveedorResponse(String id, String nit, String razonSocial, String nombreComercial, String telefono,
            String email, String direccion, String ciudad, Proveedor.Contacto contacto, Set<String> categorias,
            List<Proveedor.InsumoSuministrado> insumos, Proveedor.CondicionesComerciales condiciones, boolean activo,
            Instant creadoEn) {

        public static ProveedorResponse de(Proveedor p) {
            return new ProveedorResponse(p.getId(), p.getNit(), p.getRazonSocial(), p.getNombreComercial(), p.getTelefono(),
                    p.getEmail(), p.getDireccion(), p.getCiudad(), p.getContacto(), p.getCategorias(), p.getInsumos(),
                    p.getCondiciones(), p.isActivo(), p.getCreadoEn());
        }
    }

    @Schema(description = "Vista reducida para otros servicios")
    public record ProveedorResumen(String id, String nombreComercial, String nit, String telefono, boolean activo) {
        public static ProveedorResumen de(Proveedor p) {
            return new ProveedorResumen(p.getId(), p.getNombreComercial(), p.getNit(), p.getTelefono(), p.isActivo());
        }
    }

    // ---------- Órdenes de compra ----------
    public record ItemOrdenRequest(
            @NotBlank(message = "Seleccione el insumo") String insumoId,
            @NotNull(message = "Indique la cantidad") @DecimalMin(value = "0.001", message = "La cantidad debe ser positiva")
            @DecimalMax(value = "1000000") BigDecimal cantidad,
            @NotNull(message = "Indique el costo") @DecimalMin(value = "0", message = "El costo no puede ser negativo")
            @DecimalMax(value = "100000000") BigDecimal costoUnitario) {
    }

    @Schema(description = "Orden de compra en borrador")
    public record OrdenRequest(
            @NotBlank(message = "Seleccione el proveedor") String proveedorId,
            @NotEmpty(message = "Agregue al menos un insumo") @Valid List<ItemOrdenRequest> items,
            LocalDate fechaEntregaEsperada,
            @Size(max = 300) String notas) {
    }

    public record EntregaRequest(
            @NotNull(message = "Indique la fecha de entrega") LocalDate fechaEntrega,
            @Size(max = 60) String documento,
            @Size(max = 300) String observaciones) {
    }

    public record LineaRecepcionRequest(
            @NotBlank String insumoId,
            @NotNull @DecimalMin(value = "0", message = "No puede ser negativa") BigDecimal cantidadRecibida,
            @Size(max = 40) String lote,
            LocalDate fechaVencimiento) {
    }

    @Schema(description = "Confirmación de la mercancía recibida físicamente; aumenta el inventario")
    public record RecepcionRequest(@NotEmpty @Valid List<LineaRecepcionRequest> lineas, @Size(max = 300) String observaciones) {
    }

    public record CancelacionRequest(@NotBlank(message = "Indique el motivo") @Size(min = 5, max = 200) String motivo) {
    }

    public record OrdenResponse(String id, String numero, String proveedorId, String proveedorNombre,
            OrdenCompra.Estado estado, List<OrdenCompra.Item> items, BigDecimal total, BigDecimal totalRecibido,
            LocalDate fechaEntregaEsperada, String notas, OrdenCompra.Entrega entrega, OrdenCompra.Recepcion recepcion,
            String creadoPor, String motivoCancelacion, List<OrdenCompra.CambioEstado> historial, Instant creadoEn) {

        public static OrdenResponse de(OrdenCompra o) {
            return new OrdenResponse(o.getId(), o.getNumero(), o.getProveedorId(), o.getProveedorNombre(), o.getEstado(),
                    o.getItems(), o.getTotal(), o.getTotalRecibido(), o.getFechaEntregaEsperada(), o.getNotas(),
                    o.getEntrega(), o.getRecepcion(), o.getCreadoPor(), o.getMotivoCancelacion(), o.getHistorial(),
                    o.getCreadoEn());
        }
    }

    public record ResumenCompras(long borradores, long enviadas, long confirmadas, long entregaReportada,
            long pendientesRecepcion, long recepcionesSinSincronizar, BigDecimal comprasRecibidasMes, long proveedoresActivos) {
    }

    public record HistorialProveedor(long ordenes, long recibidas, BigDecimal totalComprado, List<OrdenResponse> ultimas) {
    }
}
