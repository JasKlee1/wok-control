package com.wokcontrol.facturacion.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.facturacion.client.PedidosClient;
import com.wokcontrol.facturacion.dto.FacturacionDtos.AutorizacionRequest;
import com.wokcontrol.facturacion.entity.AutorizacionDescuento;
import com.wokcontrol.facturacion.repository.AutorizacionDescuentoRepository;

/** Autorizaciones de descuento de un solo uso emitidas por quien tiene DESCUENTOS_AUTORIZAR. */
@Service
public class DescuentoService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final AutorizacionDescuentoRepository repo;
    private final MongoTemplate mongo;
    private final PedidosClient pedidos;
    private final AuditoriaPublisher auditoria;

    public DescuentoService(AutorizacionDescuentoRepository repo, MongoTemplate mongo, PedidosClient pedidos,
            AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.mongo = mongo;
        this.pedidos = pedidos;
        this.auditoria = auditoria;
    }

    public List<AutorizacionDescuento> recientes() {
        return repo.findTop50ByOrderByCreadaEnDesc();
    }

    public AutorizacionDescuento autorizar(AutorizacionRequest req) {
        int horas = req.horasVigencia() == null ? 4 : Math.max(1, Math.min(24, req.horasVigencia()));
        AutorizacionDescuento a = new AutorizacionDescuento();
        a.setPorcentajeMaximo(req.porcentajeMaximo());
        a.setMotivo(req.motivo().trim());
        if (req.mesaId() != null && !req.mesaId().isBlank()) {
            a.setMesaId(req.mesaId());
            a.setMesaNumero(pedidos.mesa(req.mesaId()).numero());
        }
        a.setAutorizadoPor(UsuarioActual.responsableActual());
        a.setCreadaEn(Instant.now());
        a.setExpiraEn(a.getCreadaEn().plus(horas, ChronoUnit.HOURS));
        for (int intento = 0; intento < 5; intento++) {
            a.setCodigo(codigo());
            try {
                AutorizacionDescuento g = repo.insert(a);
                auditoria.registrar("AUTORIZAR_DESCUENTO", "Descuento", g.getCodigo(),
                        g.getPorcentajeMaximo() + "% · " + g.getMotivo());
                return g;
            } catch (DuplicateKeyException ex) {
                a.setId(null);
            }
        }
        throw new IllegalStateException("No fue posible generar un código único de autorización");
    }

    /**
     * Valida y consume la autorización de forma atómica: dos cobros simultáneos no pueden usar el
     * mismo código.
     */
    public AutorizacionDescuento consumir(String codigo, BigDecimal porcentaje, String mesaId) {
        String normalizado = codigo == null ? "" : codigo.trim().toUpperCase();
        AutorizacionDescuento a = repo.findByCodigo(normalizado)
                .orElseThrow(() -> new ReglaNegocioException("Código de autorización de descuento inválido"));
        Instant ahora = Instant.now();
        if (a.isUsada()) {
            throw new ReglaNegocioException("La autorización " + normalizado + " ya fue utilizada");
        }
        if (!a.getExpiraEn().isAfter(ahora)) {
            throw new ReglaNegocioException("La autorización " + normalizado + " está vencida");
        }
        if (porcentaje.compareTo(a.getPorcentajeMaximo()) > 0) {
            throw new ReglaNegocioException("La autorización permite máximo " + a.getPorcentajeMaximo().stripTrailingZeros().toPlainString() + "%");
        }
        if (a.getMesaId() != null && !a.getMesaId().equals(mesaId)) {
            throw new ReglaNegocioException("La autorización corresponde a la mesa " + a.getMesaNumero());
        }
        AutorizacionDescuento consumida = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(a.getId()).and("usada").is(false)),
                new Update().set("usada", true).set("usadaEn", ahora).set("usadaPor", UsuarioActual.responsableActual()),
                FindAndModifyOptions.options().returnNew(true), AutorizacionDescuento.class);
        if (consumida == null) {
            throw new ReglaNegocioException("La autorización " + normalizado + " ya fue utilizada");
        }
        return consumida;
    }

    public void vincularFactura(String autorizacionId, String facturaId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(autorizacionId)), new Update().set("facturaId", facturaId),
                AutorizacionDescuento.class);
    }

    /** Devuelve una autorización consumida si el cobro no llegó a emitirse. */
    public void liberar(String autorizacionId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(autorizacionId).and("facturaId").exists(false)),
                new Update().set("usada", false).unset("usadaEn").unset("usadaPor"), AutorizacionDescuento.class);
    }

    private String codigo() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
