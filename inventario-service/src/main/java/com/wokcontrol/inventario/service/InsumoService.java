package com.wokcontrol.inventario.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.inventario.dto.InventarioDtos;
import com.wokcontrol.inventario.dto.InventarioDtos.AlertaVencimiento;
import com.wokcontrol.inventario.dto.InventarioDtos.AlertasResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.InsumoRequest;
import com.wokcontrol.inventario.dto.InventarioDtos.InsumoResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.ReporteAgotadoResponse;
import com.wokcontrol.inventario.dto.InventarioDtos.ValorizacionResponse;
import com.wokcontrol.inventario.entity.Insumo;
import com.wokcontrol.inventario.entity.ReporteAgotado;
import com.wokcontrol.inventario.repository.InsumoRepository;
import com.wokcontrol.inventario.repository.ReporteAgotadoRepository;

@Service
public class InsumoService {

    static final int DIAS_ALERTA_VENCIMIENTO = 7;

    private final InsumoRepository repo;
    private final ReporteAgotadoRepository reportes;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;
    private final ZoneId zona;

    public InsumoService(InsumoRepository repo, ReporteAgotadoRepository reportes, MongoTemplate mongo,
            AuditoriaPublisher auditoria, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.repo = repo;
        this.reportes = reportes;
        this.mongo = mongo;
        this.auditoria = auditoria;
        this.zona = ZoneId.of(zona);
    }

    public List<Insumo> buscar(String texto, String categoria, boolean soloBajoStock, boolean incluirInactivos) {
        Query q = new Query();
        if (!incluirInactivos) {
            q.addCriteria(Criteria.where("activo").is(true));
        }
        if (texto != null && !texto.isBlank()) {
            q.addCriteria(Criteria.where("nombre").regex(".*" + Pattern.quote(texto.trim()) + ".*", "i"));
        }
        if (categoria != null && !categoria.isBlank()) {
            q.addCriteria(Criteria.where("categoria").is(categoria));
        }
        q.with(Sort.by("nombre"));
        List<Insumo> lista = mongo.find(q, Insumo.class);
        return soloBajoStock ? lista.stream().filter(Insumo::isBajoStock).toList() : lista;
    }

    public List<Insumo> activos() {
        return repo.findByActivoTrueOrderByNombreAsc();
    }

    public Insumo obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Insumo", id));
    }

    public Insumo crear(InsumoRequest req) {
        validarCatalogos(req);
        if (repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe el insumo " + req.nombre());
        }
        Insumo i = new Insumo();
        i.setNombre(req.nombre().trim());
        i.setCategoria(req.categoria());
        i.setUnidad(req.unidad());
        i.setStockMinimo(req.stockMinimo());
        i.setPerecedero(req.perecedero());
        i.setCantidadDisponible(BigDecimal.ZERO);
        i.setCostoUnitario(BigDecimal.ZERO);
        Insumo g = repo.save(i);
        auditoria.registrar("CREAR", "Insumo", g.getId(), g.getNombre() + " (" + g.getUnidad() + ")");
        return g;
    }

    /**
     * Edita datos descriptivos y el stock mínimo. La cantidad no se modifica aquí: se usa un update
     * parcial para no pisar movimientos concurrentes.
     */
    public Insumo actualizar(String id, InsumoRequest req) {
        validarCatalogos(req);
        Insumo actual = obtener(id);
        if (!actual.getNombre().equalsIgnoreCase(req.nombre().trim()) && repo.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new ConflictoException("Ya existe el insumo " + req.nombre());
        }
        if (!actual.getUnidad().equals(req.unidad()) && actual.getCantidadDisponible().signum() != 0) {
            throw new ReglaNegocioException("No se puede cambiar la unidad de un insumo con existencias; ajuste a cero primero");
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("nombre", req.nombre().trim()).set("categoria", req.categoria()).set("unidad", req.unidad())
                .set("stockMinimo", req.stockMinimo()).set("perecedero", req.perecedero()).inc("version", 1), Insumo.class);
        auditoria.registrar("ACTUALIZAR", "Insumo", id, "Mínimo: " + req.stockMinimo());
        return obtener(id);
    }

    public Insumo cambiarEstado(String id, boolean activo) {
        obtener(id);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("activo", activo).inc("version", 1),
                Insumo.class);
        auditoria.registrar(activo ? "ACTIVAR" : "DESACTIVAR", "Insumo", id, null);
        return obtener(id);
    }

    public void cerrarLote(String id, String codigoLote) {
        Insumo i = obtener(id);
        List<Insumo.Lote> lotes = new ArrayList<>();
        boolean encontrado = false;
        for (Insumo.Lote l : i.getLotes()) {
            if (l.codigo().equals(codigoLote) && !l.cerrado()) {
                lotes.add(new Insumo.Lote(l.codigo(), l.cantidadInicial(), l.fechaVencimiento(), l.fechaIngreso(), l.referencia(), true));
                encontrado = true;
            } else {
                lotes.add(l);
            }
        }
        if (!encontrado) {
            throw new RecursoNoEncontradoException("Lote " + codigoLote + " no encontrado o ya cerrado");
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("lotes", lotes), Insumo.class);
        auditoria.registrar("CERRAR_LOTE", "Insumo", id, "Lote " + codigoLote);
    }

    public ReporteAgotado reportarAgotado(String insumoId, String observacion) {
        Insumo i = obtener(insumoId);
        ReporteAgotado r = reportes.save(new ReporteAgotado(i.getId(), i.getNombre(), observacion,
                UsuarioActual.responsableActual()));
        auditoria.registrar("REPORTE_AGOTADO", "Insumo", insumoId, observacion);
        return r;
    }

    public ReporteAgotado atenderReporte(String reporteId, String respuesta) {
        ReporteAgotado r = reportes.findById(reporteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reporte", reporteId));
        if (r.isAtendido()) {
            throw new ConflictoException("El reporte ya fue atendido");
        }
        r.atender(UsuarioActual.responsableActual(), respuesta);
        return reportes.save(r);
    }

    public AlertasResponse alertas() {
        List<Insumo> activos = activos();
        List<InsumoResponse> bajo = activos.stream().filter(Insumo::isBajoStock)
                .sorted(Comparator.comparing(i -> i.getCantidadDisponible().subtract(i.getStockMinimo())))
                .map(InsumoResponse::de).toList();
        LocalDate hoy = LocalDate.now(zona);
        List<AlertaVencimiento> venc = new ArrayList<>();
        for (Insumo i : activos) {
            for (Insumo.Lote l : i.getLotes()) {
                if (!l.cerrado() && l.fechaVencimiento() != null
                        && !l.fechaVencimiento().isAfter(hoy.plusDays(DIAS_ALERTA_VENCIMIENTO))) {
                    venc.add(new AlertaVencimiento(i.getId(), i.getNombre(), l.codigo(), l.fechaVencimiento(),
                            ChronoUnit.DAYS.between(hoy, l.fechaVencimiento())));
                }
            }
        }
        venc.sort(Comparator.comparing(AlertaVencimiento::fechaVencimiento));
        List<ReporteAgotadoResponse> cocina = reportes.findByAtendidoFalseOrderByFechaDesc().stream()
                .map(ReporteAgotadoResponse::de).toList();
        return new AlertasResponse(bajo, venc, cocina, bajo.size() + venc.size() + cocina.size());
    }

    public List<ReporteAgotado> reportesAtendidosRecientes() {
        return reportes.findTop20ByAtendidoTrueOrderByAtendidoEnDesc();
    }

    public ValorizacionResponse valorizacion() {
        List<Insumo> activos = activos();
        BigDecimal total = activos.stream().map(Insumo::getValorizado).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ValorizacionResponse(total, activos.size(), activos.stream().filter(Insumo::isBajoStock).count());
    }

    private static void validarCatalogos(InsumoRequest req) {
        if (!InventarioDtos.CATEGORIAS.contains(req.categoria())) {
            throw new ReglaNegocioException("Categoría inválida: " + req.categoria());
        }
        if (!InventarioDtos.UNIDADES.contains(req.unidad())) {
            throw new ReglaNegocioException("Unidad inválida: " + req.unidad());
        }
    }
}
