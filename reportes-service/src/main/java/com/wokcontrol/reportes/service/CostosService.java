package com.wokcontrol.reportes.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.wokcontrol.reportes.client.ServiciosClient;
import com.wokcontrol.reportes.dto.ReportesDtos.CostoPlato;
import com.wokcontrol.reportes.dto.ReportesDtos.InsumoResumen;
import com.wokcontrol.reportes.dto.ReportesDtos.IngredienteReceta;
import com.wokcontrol.reportes.dto.ReportesDtos.Plato;

/**
 * Costo por porción de cada plato = Σ cantidad de la receta × costo promedio actual del insumo
 * (inventario-service). Margen = precio − costo. Si un insumo no tiene costo registrado, la receta se
 * marca como incompleta para no presentar un margen engañoso.
 */
@Service
public class CostosService {

    private final ServiciosClient servicios;

    public CostosService(ServiciosClient servicios) {
        this.servicios = servicios;
    }

    public List<CostoPlato> costosPlatos() {
        java.util.function.Consumer<HttpHeaders> auth = servicios.credenciales();
        Map<String, InsumoResumen> insumos = servicios.insumos(auth).stream()
                .collect(Collectors.toMap(InsumoResumen::id, Function.identity()));
        return servicios.platos(auth).stream().map(p -> costo(p, insumos))
                .sorted(Comparator.comparing(CostoPlato::margenPorcentaje, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    static CostoPlato costo(Plato p, Map<String, InsumoResumen> insumos) {
        BigDecimal costo = BigDecimal.ZERO;
        boolean completa = p.receta() != null && !p.receta().isEmpty();
        if (p.receta() != null) {
            for (IngredienteReceta r : p.receta()) {
                InsumoResumen i = insumos.get(r.insumoId());
                if (i == null || i.costoUnitario() == null || i.costoUnitario().signum() == 0) {
                    completa = false;
                    continue;
                }
                costo = costo.add(r.cantidad().multiply(i.costoUnitario()));
            }
        }
        costo = costo.setScale(0, RoundingMode.HALF_UP);
        BigDecimal margen = p.precio() == null ? null : p.precio().subtract(costo);
        BigDecimal margenPct = completa && p.precio() != null && p.precio().signum() > 0
                ? margen.multiply(BigDecimal.valueOf(100)).divide(p.precio(), 1, RoundingMode.HALF_UP) : null;
        return new CostoPlato(p.id(), p.nombre(), p.categoria(), p.precio(), costo, completa ? margen : null, margenPct,
                completa, p.receta() == null ? 0 : p.receta().size());
    }
}
