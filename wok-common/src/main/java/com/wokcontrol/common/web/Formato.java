package com.wokcontrol.common.web;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Formateo para las vistas: {@code ${@fmt.dinero(valor)}}, {@code ${@fmt.fechaHora(instante)}}.
 * Usa la zona horaria del restaurante (por defecto America/Bogota).
 */
@Component("fmt")
public class Formato {

    private static final Locale CO = Locale.of("es", "CO");
    private final ZoneId zona;
    private final DateTimeFormatter fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final DateTimeFormatter hora = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public Formato(@Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.zona = ZoneId.of(zona);
    }

    public String dinero(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(CO);
        nf.setMaximumFractionDigits(valor.stripTrailingZeros().scale() > 0 ? 2 : 0);
        nf.setMinimumFractionDigits(0);
        return "$ " + nf.format(valor);
    }

    public String numero(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(CO);
        nf.setMaximumFractionDigits(3);
        return nf.format(valor);
    }

    public String fechaHora(Instant instante) {
        return instante == null ? "—" : fechaHora.format(instante.atZone(zona));
    }

    public String hora(Instant instante) {
        return instante == null ? "—" : hora.format(instante.atZone(zona));
    }

    public String fecha(LocalDate f) {
        return f == null ? "—" : fecha.format(f);
    }

    public String fecha(Instant instante) {
        return instante == null ? "—" : fecha.format(instante.atZone(zona));
    }

    public long minutosDesde(Instant instante) {
        return instante == null ? 0 : java.time.Duration.between(instante, Instant.now()).toMinutes();
    }

    public ZoneId zona() {
        return zona;
    }
}
