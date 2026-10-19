package com.wokcontrol.facturacion.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cierre de caja de un cajero: compara lo registrado por el sistema (por método de pago) con lo
 * contado físicamente y deja la diferencia trazada. Inmutable una vez creado.
 */
@Document("cierres_caja")
public class CierreCaja {

    @Id
    private String id;
    @Indexed(unique = true)
    private String numero;
    @Indexed
    private String cajero;
    private String cajeroNombre;
    private Instant desde;
    private Instant hasta;
    private int comprobantes;
    private int anulados;
    private BigDecimal totalVentas;
    private BigDecimal totalDescuentos;
    private BigDecimal totalImpuestos;
    private Map<String, BigDecimal> sistemaPorMetodo = new LinkedHashMap<>();
    private Map<String, BigDecimal> declaradoPorMetodo = new LinkedHashMap<>();
    private Map<String, BigDecimal> diferenciaPorMetodo = new LinkedHashMap<>();
    private BigDecimal diferenciaTotal;
    private String observaciones;
    private Instant creadoEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getCajero() { return cajero; }
    public void setCajero(String cajero) { this.cajero = cajero; }
    public String getCajeroNombre() { return cajeroNombre; }
    public void setCajeroNombre(String cajeroNombre) { this.cajeroNombre = cajeroNombre; }
    public Instant getDesde() { return desde; }
    public void setDesde(Instant desde) { this.desde = desde; }
    public Instant getHasta() { return hasta; }
    public void setHasta(Instant hasta) { this.hasta = hasta; }
    public int getComprobantes() { return comprobantes; }
    public void setComprobantes(int comprobantes) { this.comprobantes = comprobantes; }
    public int getAnulados() { return anulados; }
    public void setAnulados(int anulados) { this.anulados = anulados; }
    public BigDecimal getTotalVentas() { return totalVentas; }
    public void setTotalVentas(BigDecimal totalVentas) { this.totalVentas = totalVentas; }
    public BigDecimal getTotalDescuentos() { return totalDescuentos; }
    public void setTotalDescuentos(BigDecimal v) { this.totalDescuentos = v; }
    public BigDecimal getTotalImpuestos() { return totalImpuestos; }
    public void setTotalImpuestos(BigDecimal v) { this.totalImpuestos = v; }
    public Map<String, BigDecimal> getSistemaPorMetodo() { return sistemaPorMetodo; }
    public void setSistemaPorMetodo(Map<String, BigDecimal> m) { this.sistemaPorMetodo = m; }
    public Map<String, BigDecimal> getDeclaradoPorMetodo() { return declaradoPorMetodo; }
    public void setDeclaradoPorMetodo(Map<String, BigDecimal> m) { this.declaradoPorMetodo = m; }
    public Map<String, BigDecimal> getDiferenciaPorMetodo() { return diferenciaPorMetodo; }
    public void setDiferenciaPorMetodo(Map<String, BigDecimal> m) { this.diferenciaPorMetodo = m; }
    public BigDecimal getDiferenciaTotal() { return diferenciaTotal; }
    public void setDiferenciaTotal(BigDecimal diferenciaTotal) { this.diferenciaTotal = diferenciaTotal; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
