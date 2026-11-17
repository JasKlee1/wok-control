package com.wokcontrol.pedidos.entity;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("mesas")
public class Mesa {

    /**
     * v2 · HU-044: POR_LIMPIAR es la mesa cuyo grupo ya se fue y aún no está limpia. DISPONIBLE es la
     * mesa «Libre» de la épica 8 (no se renombra para no migrar datos).
     */
    public enum Estado {
        DISPONIBLE("Libre"), OCUPADA("Ocupada"), POR_LIMPIAR("Por limpiar"), FUERA_DE_SERVICIO("Fuera de servicio");

        private final String etiqueta;

        Estado(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    /**
     * v2 · HU-042/045: grupo sentado por el anfitrión, con su mesero. Si es una unión, todas las mesas
     * unidas comparten {@code unionId}. Solo existe mientras el grupo está sentado.
     */
    public record Asignacion(String grupoId, String grupo, int personas, String mesero, String meseroNombre,
            Instant sentadaEn, String unionId, List<Integer> unionNumeros) {

        public boolean esUnion() {
            return unionId != null;
        }
    }

    @Id
    private String id;

    @Indexed(unique = true)
    private int numero;
    private int capacidad;
    private String zona;
    private Estado estado = Estado.DISPONIBLE;
    private boolean activa = true;
    private Instant ocupadaDesde;
    /** v2 · HU-033: código aleatorio impreso en el QR de la mesa (no se deduce del número ni del id). */
    @Indexed(unique = true, sparse = true)
    private String codigoQr;
    /** v2 · HU-035: momento en que el cliente pidió la cuenta desde el celular; se borra al cerrar la sesión. */
    private Instant cuentaSolicitadaEn;
    /** v2 · HU-042: grupo y mesero asignados por el anfitrión. */
    private Asignacion asignacion;
    /** v2 · HU-044: desde cuándo la mesa espera que la limpien. */
    private Instant porLimpiarDesde;

    @CreatedDate
    private Instant creadoEn;
    @LastModifiedDate
    private Instant actualizadoEn;
    @Version
    private Long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public int getCapacidad() { return capacidad; }
    public void setCapacidad(int capacidad) { this.capacidad = capacidad; }
    public String getZona() { return zona; }
    public void setZona(String zona) { this.zona = zona; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public Instant getOcupadaDesde() { return ocupadaDesde; }
    public void setOcupadaDesde(Instant ocupadaDesde) { this.ocupadaDesde = ocupadaDesde; }
    public String getCodigoQr() { return codigoQr; }
    public void setCodigoQr(String codigoQr) { this.codigoQr = codigoQr; }
    public Instant getCuentaSolicitadaEn() { return cuentaSolicitadaEn; }
    public void setCuentaSolicitadaEn(Instant cuentaSolicitadaEn) { this.cuentaSolicitadaEn = cuentaSolicitadaEn; }
    public Asignacion getAsignacion() { return asignacion; }
    public void setAsignacion(Asignacion asignacion) { this.asignacion = asignacion; }
    public Instant getPorLimpiarDesde() { return porLimpiarDesde; }
    public void setPorLimpiarDesde(Instant porLimpiarDesde) { this.porLimpiarDesde = porLimpiarDesde; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
