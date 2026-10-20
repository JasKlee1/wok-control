package com.wokcontrol.menu.service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.menu.entity.Categoria;
import com.wokcontrol.menu.entity.Combo;
import com.wokcontrol.menu.entity.Imagen;
import com.wokcontrol.menu.entity.Plato;
import com.wokcontrol.menu.repository.CategoriaRepository;
import com.wokcontrol.menu.repository.ComboRepository;
import com.wokcontrol.menu.repository.PlatoRepository;

/**
 * Fotos de la carta guardadas en GridFS (base wok_menu). Cada plato, combo o categoría tiene a lo
 * sumo una foto: al reemplazarla se borra el archivo anterior para no dejar huérfanos.
 */
@Service
public class ImagenService {

    public static final int TAMANO_MAXIMO = 2 * 1024 * 1024;

    public enum Destino { PLATO, COMBO, CATEGORIA }

    /** Crédito opcional: obligatorio en la práctica para fotos con licencia CC BY / CC BY-SA. */
    public record Credito(String autor, String licencia, String licenciaUrl, String fuenteUrl) {
        public static final Credito NINGUNO = new Credito(null, null, null, null);
    }

    public record CreditoListado(String elemento, String tipo, Imagen imagen) {
    }

    private final GridFsTemplate gridFs;
    private final PlatoRepository platos;
    private final ComboRepository combos;
    private final CategoriaRepository categorias;
    private final AuditoriaPublisher auditoria;

    public ImagenService(GridFsTemplate gridFs, PlatoRepository platos, ComboRepository combos,
            CategoriaRepository categorias, AuditoriaPublisher auditoria) {
        this.gridFs = gridFs;
        this.platos = platos;
        this.combos = combos;
        this.categorias = categorias;
        this.auditoria = auditoria;
    }

    public Imagen asignar(Destino destino, String id, byte[] datos, Credito credito) {
        String tipo = tipoContenido(datos);
        Credito c = credito == null ? Credito.NINGUNO : credito;
        Document meta = new Document("destino", destino.name()).append("elementoId", id);
        ObjectId archivo = gridFs.store(new ByteArrayInputStream(datos), destino.name().toLowerCase() + "-" + id, tipo, meta);
        Imagen nueva = new Imagen(archivo.toHexString(), tipo, recortar(c.autor(), 200), recortar(c.licencia(), 60),
                url(c.licenciaUrl()), url(c.fuenteUrl()));
        Imagen anterior = reemplazar(destino, id, nueva);
        borrarArchivo(anterior);
        auditoria.registrar("ASIGNAR_IMAGEN", nombreEntidad(destino), id, nueva.licencia() != null ? nueva.licencia() : "foto propia");
        return nueva;
    }

    public void quitar(Destino destino, String id) {
        borrarArchivo(reemplazar(destino, id, null));
        auditoria.registrar("QUITAR_IMAGEN", nombreEntidad(destino), id, null);
    }

    public Optional<GridFsResource> abrir(String imagenId) {
        if (!ObjectId.isValid(imagenId)) {
            return Optional.empty();
        }
        var archivo = gridFs.findOne(Query.query(Criteria.where("_id").is(new ObjectId(imagenId))));
        return archivo == null ? Optional.empty() : Optional.of(gridFs.getResource(archivo));
    }

    /** Fotos en uso con su crédito, para la página de créditos que exigen las licencias libres. */
    public List<CreditoListado> creditos() {
        List<CreditoListado> r = new ArrayList<>();
        categorias.findAll().stream().filter(c -> c.getImagen() != null)
                .forEach(c -> r.add(new CreditoListado(c.getNombre(), "Categoría", c.getImagen())));
        platos.findByActivoTrueOrderByNombreAsc().stream().filter(p -> p.getImagen() != null)
                .forEach(p -> r.add(new CreditoListado(p.getNombre(), "Plato", p.getImagen())));
        combos.findAllByOrderByNombreAsc().stream().filter(c -> c.getImagen() != null)
                .forEach(c -> r.add(new CreditoListado(c.getNombre(), "Combo", c.getImagen())));
        return r;
    }

    /** Reconoce el formato por su firma binaria: no se confía en la extensión ni en el tipo declarado. */
    static String tipoContenido(byte[] d) {
        if (d == null || d.length == 0) {
            throw new ReglaNegocioException("Seleccione una imagen");
        }
        if (d.length > TAMANO_MAXIMO) {
            throw new ReglaNegocioException("La imagen supera el tamaño máximo de 2 MB");
        }
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        throw new ReglaNegocioException("Formato no admitido: use JPG, PNG o WebP");
    }

    private Imagen reemplazar(Destino destino, String id, Imagen nueva) {
        Imagen anterior;
        switch (destino) {
            case PLATO -> {
                Plato p = platos.findById(id).orElseThrow(() -> new ReglaNegocioException("El plato no existe"));
                anterior = p.getImagen();
                p.setImagen(nueva);
                platos.save(p);
            }
            case COMBO -> {
                Combo c = combos.findById(id).orElseThrow(() -> new ReglaNegocioException("El combo no existe"));
                anterior = c.getImagen();
                c.setImagen(nueva);
                combos.save(c);
            }
            default -> {
                Categoria c = categorias.findById(id).orElseThrow(() -> new ReglaNegocioException("La categoría no existe"));
                anterior = c.getImagen();
                c.setImagen(nueva);
                categorias.save(c);
            }
        }
        return anterior;
    }

    private void borrarArchivo(Imagen imagen) {
        if (imagen != null && ObjectId.isValid(imagen.id())) {
            gridFs.delete(Query.query(Criteria.where("_id").is(new ObjectId(imagen.id()))));
        }
    }

    private static String nombreEntidad(Destino d) {
        return switch (d) {
            case PLATO -> "Plato";
            case COMBO -> "Combo";
            case CATEGORIA -> "Categoria";
        };
    }

    private static String recortar(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Solo enlaces http(s): evita que un crédito se convierta en un enlace javascript: en la página. */
    private static String url(String s) {
        String t = recortar(s, 500);
        return t != null && (t.startsWith("https://") || t.startsWith("http://")) ? t : null;
    }
}
