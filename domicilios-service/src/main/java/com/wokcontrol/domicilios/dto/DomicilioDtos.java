package com.wokcontrol.domicilios.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.wokcontrol.domicilios.entity.Domicilio;
import com.wokcontrol.domicilios.entity.EstadoDomicilio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DomicilioDtos {

    /** AC-047.2: 10 dígitos (celular o fijo con indicativo); se aceptan espacios, guiones y +57. */
    public static final String TELEFONO = "^(\\+?57)?[\\s.-]*(\\d[\\s.-]*){10}$";
    public static final String MENSAJE_TELEFONO = "El teléfono debe tener 10 dígitos";
    public static final String MENSAJE_DIRECCION = "La dirección es obligatoria";

    private DomicilioDtos() {
    }

    public enum TipoProducto { PLATO, COMBO }

    public record ItemRequest(
            @NotNull(message = "Indique si es PLATO o COMBO") TipoProducto tipo,
            @NotBlank(message = "Indique el producto") String productoId,
            @Min(value = 1, message = "La cantidad mínima es 1") @Max(value = 50, message = "Máximo 50 unidades") int cantidad,
            @Min(0) @Max(3) int nivelPicante,
            @Size(max = 200) String notas) {
    }

    /** HU-047: registrar un domicilio (AC-047.1, AC-047.2). */
    public record DomicilioRequest(
            @NotBlank(message = "El nombre del cliente es obligatorio") @Size(max = 80) String cliente,
            @NotBlank(message = MENSAJE_TELEFONO) @Pattern(regexp = TELEFONO, message = MENSAJE_TELEFONO) String telefono,
            @NotBlank(message = MENSAJE_DIRECCION) @Size(max = 150) String direccion,
            @Size(max = 150) @Schema(description = "Barrio, torre, apartamento o punto de referencia") String referencia,
            @NotNull(message = "Indique el valor del domicilio") @DecimalMin(value = "0", message = "No puede ser negativo")
            @DecimalMax(value = "100000", message = "Máximo $100.000") BigDecimal valorDomicilio,
            @NotEmpty(message = "Agregue al menos un plato") @Valid List<ItemRequest> items,
            @Size(max = 300) String observaciones) {
    }

    /** HU-047: lo que el cajero puede corregir antes del despacho (los platos ya están en cocina). */
    public record EdicionRequest(
            @NotBlank(message = "El nombre del cliente es obligatorio") @Size(max = 80) String cliente,
            @NotBlank(message = MENSAJE_TELEFONO) @Pattern(regexp = TELEFONO, message = MENSAJE_TELEFONO) String telefono,
            @NotBlank(message = MENSAJE_DIRECCION) @Size(max = 150) String direccion,
            @Size(max = 150) String referencia,
            @NotNull(message = "Indique el valor del domicilio") @DecimalMin(value = "0", message = "No puede ser negativo")
            @DecimalMax(value = "100000", message = "Máximo $100.000") BigDecimal valorDomicilio,
            @Size(max = 300) String observaciones) {
    }

    public record MotivoRequest(@Size(max = 200) String motivo) {
    }

    public record AsignacionRequest(@NotBlank(message = "Seleccione el domiciliario") String domiciliario) {
    }

    /** HU-050: métodos que acepta el domiciliario (datáfono o efectivo). */
    public enum MetodoCobro {
        EFECTIVO("Efectivo"), TARJETA_DEBITO("Tarjeta débito"), TARJETA_CREDITO("Tarjeta crédito");

        private final String etiqueta;

        MetodoCobro(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    public record CobroRequest(
            @NotNull(message = "Seleccione el método de pago") MetodoCobro metodo,
            @NotNull(message = "Indique el monto recibido") @DecimalMin(value = "1", message = "El monto debe ser positivo")
            @DecimalMax(value = "100000000") BigDecimal monto,
            @Size(max = 60) @Schema(description = "Número de aprobación del datáfono") String referencia) {
    }

    public record Domiciliario(String username, String nombre, boolean disponible, long enCamino, long asignados) {
    }

    public record DomicilioResponse(String id, String numero, EstadoDomicilio estado, String estadoEtiqueta,
            Domicilio.Cliente cliente, List<Domicilio.Linea> lineas, String observaciones, BigDecimal subtotalPlatos,
            BigDecimal impuestoPorcentaje, BigDecimal impuestoValor, BigDecimal valorDomicilio, BigDecimal total,
            String pedidoId, String pedidoNumero, String registradoPor, String domiciliario, String domiciliarioNombre,
            Domicilio.Cobro cobro, String motivoNoEntrega, String motivoCancelacion, Instant creadoEn, Instant asignadoEn,
            Instant enCaminoEn, Instant entregadoEn, String urlRuta) {

        public static DomicilioResponse de(Domicilio d, String urlRuta) {
            return new DomicilioResponse(d.getId(), d.getNumero(), d.getEstado(), d.getEstado().etiqueta(), d.getCliente(),
                    d.getLineas(), d.getObservaciones(), d.getSubtotalPlatos(), d.getImpuestoPorcentaje(), d.getImpuestoValor(),
                    d.getValorDomicilio(), d.getTotal(), d.getPedidoId(), d.getPedidoNumero(), d.getRegistradoPor(),
                    d.getDomiciliario(), d.getDomiciliarioNombre(), d.getCobro(), d.getMotivoNoEntrega(),
                    d.getMotivoCancelacion(), d.getCreadoEn(), d.getAsignadoEn(), d.getEnCaminoEn(), d.getEntregadoEn(), urlRuta);
        }
    }
}
