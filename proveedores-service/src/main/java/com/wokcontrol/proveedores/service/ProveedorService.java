package com.wokcontrol.proveedores.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ConflictoException;
import com.wokcontrol.common.exception.OperacionNoPermitidaException;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.proveedores.client.InventarioClient;
import com.wokcontrol.proveedores.client.InventarioClient.InsumoResumen;
import com.wokcontrol.proveedores.dto.ProveedorDtos.PerfilProveedorRequest;
import com.wokcontrol.proveedores.dto.ProveedorDtos.ProveedorRequest;
import com.wokcontrol.proveedores.entity.Proveedor;
import com.wokcontrol.proveedores.repository.ProveedorRepository;

@Service
public class ProveedorService {

    public static final List<String> CATEGORIAS = List.of("PROTEINAS", "VEGETALES", "GRANOS", "FIDEOS", "SALSAS",
            "ESPECIAS", "ACEITES", "LACTEOS", "BEBIDAS", "DESECHABLES", "LIMPIEZA", "OTROS");

    private final ProveedorRepository repo;
    private final InventarioClient inventario;
    private final MongoTemplate mongo;
    private final AuditoriaPublisher auditoria;

    public ProveedorService(ProveedorRepository repo, InventarioClient inventario, MongoTemplate mongo,
            AuditoriaPublisher auditoria) {
        this.repo = repo;
        this.inventario = inventario;
        this.mongo = mongo;
        this.auditoria = auditoria;
    }

    public List<Proveedor> buscar(String texto, Boolean activo, String categoria, String insumoId) {
        Query q = new Query();
        if (texto != null && !texto.isBlank()) {
            String rx = ".*" + Pattern.quote(texto.trim()) + ".*";
            q.addCriteria(new Criteria().orOperator(Criteria.where("nombreComercial").regex(rx, "i"),
                    Criteria.where("razonSocial").regex(rx, "i"), Criteria.where("nit").regex(rx, "i")));
        }
        if (activo != null) {
            q.addCriteria(Criteria.where("activo").is(activo));
        }
        if (categoria != null && !categoria.isBlank()) {
            q.addCriteria(Criteria.where("categorias").is(categoria));
        }
        if (insumoId != null && !insumoId.isBlank()) {
            q.addCriteria(Criteria.where("insumos.insumoId").is(insumoId));
        }
        q.with(Sort.by("nombreComercial"));
        return mongo.find(q, Proveedor.class);
    }

    public Proveedor obtener(String id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proveedor", id));
    }

    /** Perfil del proveedor autenticado: solo puede ver el suyo (claim proveedorId del JWT). */
    public Proveedor miPerfil() {
        String proveedorId = UsuarioActual.requerido().proveedorId();
        if (proveedorId == null) {
            throw new OperacionNoPermitidaException("Su usuario no está vinculado a ningún proveedor");
        }
        return obtener(proveedorId);
    }

    public Proveedor crear(ProveedorRequest req) {
        String nit = req.nit().trim();
        if (repo.existsByNit(nit)) {
            throw new ConflictoException("Ya existe un proveedor con NIT " + nit);
        }
        Proveedor p = new Proveedor();
        p.setNit(nit);
        aplicar(p, req);
        Proveedor g = repo.save(p);
        auditoria.registrar("CREAR", "Proveedor", g.getId(), g.getNombreComercial() + " NIT " + nit);
        return g;
    }

    public Proveedor actualizar(String id, ProveedorRequest req) {
        Proveedor p = obtener(id);
        String nit = req.nit().trim();
        if (!nit.equals(p.getNit()) && repo.existsByNit(nit)) {
            throw new ConflictoException("Ya existe un proveedor con NIT " + nit);
        }
        p.setNit(nit);
        aplicar(p, req);
        Proveedor g = repo.save(p);
        auditoria.registrar("ACTUALIZAR", "Proveedor", id, g.getNombreComercial());
        return g;
    }

    public Proveedor actualizarMiPerfil(PerfilProveedorRequest req) {
        Proveedor p = miPerfil();
        p.setTelefono(req.telefono().trim());
        p.setEmail(vacio(req.email()));
        p.setDireccion(vacio(req.direccion()));
        Proveedor.Contacto c = p.getContacto();
        p.setContacto(new Proveedor.Contacto(vacio(req.contactoNombre()), c == null ? null : c.cargo(),
                vacio(req.contactoTelefono()), vacio(req.contactoEmail())));
        Proveedor g = repo.save(p);
        auditoria.registrar("ACTUALIZAR_PERFIL", "Proveedor", p.getId(), "Actualizado por el proveedor");
        return g;
    }

    public Proveedor cambiarEstado(String id, boolean activo) {
        Proveedor p = obtener(id);
        p.setActivo(activo);
        Proveedor g = repo.save(p);
        auditoria.registrar(activo ? "ACTIVAR" : "DESACTIVAR", "Proveedor", id, null);
        return g;
    }

    private void aplicar(Proveedor p, ProveedorRequest req) {
        LinkedHashSet<String> categorias = new LinkedHashSet<>();
        if (req.categorias() != null) {
            for (String c : req.categorias()) {
                if (!CATEGORIAS.contains(c)) {
                    throw new ReglaNegocioException("Categoría inválida: " + c);
                }
                categorias.add(c);
            }
        }
        p.setRazonSocial(req.razonSocial().trim());
        p.setNombreComercial(req.nombreComercial().trim());
        p.setTelefono(req.telefono().trim());
        p.setEmail(vacio(req.email()));
        p.setDireccion(vacio(req.direccion()));
        p.setCiudad(vacio(req.ciudad()));
        p.setContacto(new Proveedor.Contacto(vacio(req.contactoNombre()), vacio(req.contactoCargo()),
                vacio(req.contactoTelefono()), vacio(req.contactoEmail())));
        p.setCategorias(categorias);
        p.setInsumos(resolverInsumos(req.insumoIds()));
        p.setCondiciones(new Proveedor.CondicionesComerciales(req.plazoPagoDias(),
                req.pedidoMinimo() == null ? BigDecimal.ZERO : req.pedidoMinimo(), req.tiempoEntregaDias(),
                vacio(req.formaPago()), vacio(req.observacionesComerciales())));
    }

    /** Valida los insumos asociados contra inventario-service y guarda su nombre como copia. */
    private List<Proveedor.InsumoSuministrado> resolverInsumos(java.util.Set<String> ids) {
        List<Proveedor.InsumoSuministrado> r = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return r;
        }
        Map<String, InsumoResumen> insumos = inventario.insumos().stream()
                .collect(Collectors.toMap(InsumoResumen::id, Function.identity()));
        for (String id : ids) {
            InsumoResumen i = insumos.get(id);
            if (i == null) {
                throw new ReglaNegocioException("El insumo " + id + " no existe o está inactivo");
            }
            r.add(new Proveedor.InsumoSuministrado(i.id(), i.nombre(), i.unidad()));
        }
        return r;
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
