package com.wokcontrol.facturacion.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.mongo.SecuenciaService;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.facturacion.dto.FacturacionDtos.PreliminarCierre;
import com.wokcontrol.facturacion.entity.CierreCaja;
import com.wokcontrol.facturacion.entity.Factura;
import com.wokcontrol.facturacion.repository.CierreCajaRepository;
import com.wokcontrol.facturacion.repository.FacturaRepository;

/**
 * Cierre de caja por cajero: agrupa los comprobantes aún no cerrados, calcula lo recibido por
 * método de pago (neto del cambio en efectivo) y lo compara con lo contado físicamente.
 */
@Service
public class CierreCajaService {

    private final FacturaRepository facturas;
    private final CierreCajaRepository repo;
    private final SecuenciaService secuencias;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;

    public CierreCajaService(FacturaRepository facturas, CierreCajaRepository repo, SecuenciaService secuencias,
            MongoTemplate mongo, AuditoriaPublisher auditoria) {
        this.facturas = facturas;
        this.repo = repo;
        this.secuencias = secuencias;
        this.mongo = mongo;
        this.auditoria = auditoria;
    }

    public List<CierreCaja> recientes() {
        return repo.findTop100ByOrderByCreadoEnDesc();
    }

    public CierreCaja obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cierre de caja", id));
    }

    public List<Factura> comprobantesDe(String cierreId) {
        return facturas.findByCierreIdOrderByEmitidaEnAsc(cierreId);
    }

    public PreliminarCierre preliminar(String cajero) {
        List<Factura> abiertas = facturas.findByCajeroAndCierreIdIsNullOrderByEmitidaEnAsc(cajero);
        return calcular(cajero, abiertas);
    }

    static PreliminarCierre calcular(String cajero, List<Factura> abiertas) {
        List<Factura> emitidas = abiertas.stream().filter(f -> f.getEstado() == Factura.Estado.EMITIDA).toList();
        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        for (Factura.MetodoPago m : Factura.MetodoPago.values()) {
            porMetodo.put(m.name(), emitidas.stream().map(f -> f.netoPorMetodo(m)).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        Instant desde = abiertas.isEmpty() ? null : abiertas.get(0).getEmitidaEn();
        return new PreliminarCierre(cajero, desde, Instant.now(), emitidas.size(), abiertas.size() - emitidas.size(),
                emitidas.stream().map(Factura::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                emitidas.stream().map(f -> f.getDescuento() == null ? BigDecimal.ZERO : f.getDescuento().valor())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                emitidas.stream().map(Factura::getImpuestoValor).reduce(BigDecimal.ZERO, BigDecimal::add), porMetodo);
    }

    public CierreCaja cerrar(Map<Factura.MetodoPago, BigDecimal> declarado, String observaciones) {
        UsuarioActual u = UsuarioActual.requerido();
        List<Factura> abiertas = facturas.findByCajeroAndCierreIdIsNullOrderByEmitidaEnAsc(u.responsable());
        if (abiertas.isEmpty()) {
            throw new ReglaNegocioException("No tiene comprobantes pendientes de cierre");
        }
        PreliminarCierre p = calcular(u.responsable(), abiertas);
        CierreCaja c = new CierreCaja();
        c.setNumero(secuencias.siguienteFormateado("cierres", "CC-"));
        c.setCajero(u.responsable());
        c.setCajeroNombre(u.nombre());
        c.setDesde(p.desde());
        c.setHasta(p.hasta());
        c.setComprobantes(p.comprobantes());
        c.setAnulados(p.anulados());
        c.setTotalVentas(p.totalVentas());
        c.setTotalDescuentos(p.totalDescuentos());
        c.setTotalImpuestos(p.totalImpuestos());
        c.setSistemaPorMetodo(p.sistemaPorMetodo());
        BigDecimal diferenciaTotal = BigDecimal.ZERO;
        for (Factura.MetodoPago m : Factura.MetodoPago.values()) {
            BigDecimal contado = declarado == null || declarado.get(m) == null ? BigDecimal.ZERO : declarado.get(m);
            if (contado.signum() < 0) {
                throw new ReglaNegocioException("Los montos declarados no pueden ser negativos");
            }
            BigDecimal dif = contado.subtract(p.sistemaPorMetodo().get(m.name()));
            c.getDeclaradoPorMetodo().put(m.name(), contado);
            c.getDiferenciaPorMetodo().put(m.name(), dif);
            diferenciaTotal = diferenciaTotal.add(dif);
        }
        c.setDiferenciaTotal(diferenciaTotal);
        if (diferenciaTotal.signum() != 0 && (observaciones == null || observaciones.trim().length() < 5)) {
            throw new ReglaNegocioException("Hay una diferencia de $" + diferenciaTotal.toPlainString()
                    + ": explique la causa en las observaciones");
        }
        c.setObservaciones(observaciones == null || observaciones.isBlank() ? null : observaciones.trim());
        c.setCreadoEn(Instant.now());
        CierreCaja g = repo.save(c);
        // marca los comprobantes como cerrados (solo los que seguían abiertos)
        mongo.updateMulti(Query.query(Criteria.where("_id").in(abiertas.stream().map(Factura::getId).toList())
                .and("cierreId").isNull()), new Update().set("cierreId", g.getId()), Factura.class);
        auditoria.registrar("CIERRE_CAJA", "CierreCaja", g.getNumero(), "Ventas $" + g.getTotalVentas()
                + " · diferencia $" + g.getDiferenciaTotal());
        return g;
    }
}
