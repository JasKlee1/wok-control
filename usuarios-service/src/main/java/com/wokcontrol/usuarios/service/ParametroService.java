package com.wokcontrol.usuarios.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.usuarios.dto.ParametrosDtos.ParametrosRequest;
import com.wokcontrol.usuarios.entity.ParametrosGenerales;
import com.wokcontrol.usuarios.repository.ParametrosRepository;

@Service
public class ParametroService {

    private final ParametrosRepository repo;
    private final AuditoriaPublisher auditoria;

    public ParametroService(ParametrosRepository repo, AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.auditoria = auditoria;
    }

    public ParametrosGenerales obtener() {
        return repo.findById(ParametrosGenerales.ID).orElseGet(ParametrosGenerales::new);
    }

    public ParametrosGenerales actualizar(ParametrosRequest req, String actor) {
        ParametrosGenerales p = obtener();
        p.setNombreRestaurante(req.nombreRestaurante().trim());
        p.setNit(vacioANull(req.nit()));
        p.setDireccion(vacioANull(req.direccion()));
        p.setTelefono(vacioANull(req.telefono()));
        p.setImpuestoConsumoPorcentaje(req.impuestoConsumoPorcentaje());
        p.setMensajeComprobante(vacioANull(req.mensajeComprobante()));
        p.setMinutosAlertaCocina(req.minutosAlertaCocina());
        p.setActualizadoPor(actor);
        p.setActualizadoEn(Instant.now());
        ParametrosGenerales guardado = repo.save(p);
        auditoria.registrar("ACTUALIZAR", "Parametros", ParametrosGenerales.ID,
                "Impuesto consumo: " + req.impuestoConsumoPorcentaje() + "%");
        return guardado;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
