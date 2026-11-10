package com.wokcontrol.pedidos.service;

import java.time.Instant;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.TokenService;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.entity.SesionMesa;
import com.wokcontrol.pedidos.repository.MesaRepository;
import com.wokcontrol.pedidos.repository.SesionMesaRepository;

/**
 * v2 · Épica 7 · T002 (HU-036) y T004 (HU-033): abre, valida y cierra la sesión de mesa del cliente.
 * <ul>
 * <li>REQ-WOK-033: el QR de una mesa activa abre una sesión; si la mesa no existe, está inactiva o
 * fuera de servicio, no se abre sesión.</li>
 * <li>REQ-WOK-036: la sesión solo sirve para su mesa y vence cuando se cobra la cuenta.</li>
 * </ul>
 */
@Service
public class SesionMesaService {

    public static final String MENSAJE_MESA_NO_DISPONIBLE = "Mesa no disponible, pide ayuda al personal";

    /** AC-033.2: el QR no corresponde a una mesa que pueda atender clientes. */
    public static class MesaNoDisponibleException extends ReglaNegocioException {
        public MesaNoDisponibleException() {
            super(MENSAJE_MESA_NO_DISPONIBLE);
        }
    }

    /** AC-036.2: la cuenta ya se cobró (o la sesión no es de esta mesa); hay que escanear de nuevo. */
    public static class SesionVencidaException extends ReglaNegocioException {
        public SesionVencidaException() {
            super("La sesión de la mesa terminó. Escanea el QR de tu mesa para volver a entrar");
        }
    }

    public record SesionAbierta(String sesionId, int mesaNumero, String token, Instant expiraEn) {
    }

    private final MesaRepository mesas;
    private final SesionMesaRepository repo;
    private final MongoTemplate mongo;
    private final TokenService tokens;
    private final AuditoriaPublisher auditoria;

    public SesionMesaService(MesaRepository mesas, SesionMesaRepository repo, MongoTemplate mongo, TokenService tokens,
            AuditoriaPublisher auditoria) {
        this.mesas = mesas;
        this.repo = repo;
        this.mongo = mongo;
        this.tokens = tokens;
        this.auditoria = auditoria;
    }

    public SesionAbierta abrir(String codigoQr) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new MesaNoDisponibleException();
        }
        Mesa mesa = mesas.findByCodigoQr(codigoQr.trim())
                .filter(Mesa::isActiva)
                .filter(m -> m.getEstado() != Mesa.Estado.FUERA_DE_SERVICIO)
                .orElseThrow(MesaNoDisponibleException::new);
        SesionMesa s = new SesionMesa();
        s.setMesaId(mesa.getId());
        s.setMesaNumero(mesa.getNumero());
        s.setEstado(SesionMesa.Estado.ACTIVA);
        s.setAbiertaEn(Instant.now());
        SesionMesa g = repo.save(s);
        TokenService.TokenEmitido token = tokens.emitirTokenMesa(g.getId(), mesa.getId(), mesa.getNumero());
        auditoria.registrar("ABRIR_SESION_QR", "Mesa", mesa.getId(), "Sesión de cliente en la mesa " + mesa.getNumero());
        return new SesionAbierta(g.getId(), mesa.getNumero(), token.token(), token.expiraEn());
    }

    /** Devuelve la sesión si sigue activa y pertenece a la mesa del token; si no, la rechaza. */
    public SesionMesa validar(String sesionId, String mesaId) {
        if (sesionId == null || mesaId == null) {
            throw new SesionVencidaException();
        }
        return repo.findById(sesionId)
                .filter(s -> s.getEstado() == SesionMesa.Estado.ACTIVA)
                .filter(s -> mesaId.equals(s.getMesaId()))
                .orElseThrow(SesionVencidaException::new);
    }

    /** Cierra todas las sesiones activas de la mesa (al cobrar la cuenta o al liberar la mesa). */
    public long cerrarDeMesa(String mesaId, String motivo) {
        long cerradas = mongo.updateMulti(
                Query.query(Criteria.where("mesaId").is(mesaId).and("estado").is(SesionMesa.Estado.ACTIVA)),
                new Update().set("estado", SesionMesa.Estado.CERRADA).set("cerradaEn", Instant.now())
                        .set("motivoCierre", motivo),
                SesionMesa.class).getModifiedCount();
        // v2 · HU-035: con la cuenta cerrada, la mesa deja de figurar como «Cuenta solicitada»
        mongo.updateFirst(Query.query(Criteria.where("_id").is(mesaId)), new Update().unset("cuentaSolicitadaEn"), Mesa.class);
        if (cerradas > 0) {
            auditoria.registrar("CERRAR_SESION_QR", "Mesa", mesaId, motivo + " (" + cerradas + " sesión/es)");
        }
        return cerradas;
    }
}
