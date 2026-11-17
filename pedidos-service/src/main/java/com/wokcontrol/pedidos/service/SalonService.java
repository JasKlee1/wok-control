package com.wokcontrol.pedidos.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.audit.AuditoriaPublisher;
import com.wokcontrol.common.exception.RecursoNoEncontradoException;
import com.wokcontrol.common.exception.ReglaNegocioException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.pedidos.client.UsuariosClient;
import com.wokcontrol.pedidos.dto.PedidoDtos.MesaResponse;
import com.wokcontrol.pedidos.dto.SalonDtos;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignacionResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.AsignarRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.GrupoRequest;
import com.wokcontrol.pedidos.dto.SalonDtos.GrupoResponse;
import com.wokcontrol.pedidos.dto.SalonDtos.MapaSalon;
import com.wokcontrol.pedidos.entity.GrupoEspera;
import com.wokcontrol.pedidos.entity.Mesa;
import com.wokcontrol.pedidos.repository.GrupoEsperaRepository;
import com.wokcontrol.pedidos.repository.MesaRepository;

/**
 * v2 · Épica 8: lo que hace el anfitrión. Lista de espera (HU-040), mapa del salón (HU-041),
 * asignación de mesa y mesero (HU-042) con unión de mesas (HU-045) y la huella del mapa en vivo
 * (HU-046). El paso a «por limpiar» y «marcar limpia» (HU-044) están en {@link MesaService}.
 */
@Service
public class SalonService {

    public static final String MESA_NO_DISPONIBLE = "MESA_NO_DISPONIBLE";
    public static final String CAPACIDAD_INSUFICIENTE = "CAPACIDAD_INSUFICIENTE";
    private static final int MAX_MESAS_UNION = 6;

    private final GrupoEsperaRepository espera;
    private final MesaRepository mesasRepo;
    private final MesaService mesas;
    private final UsuariosClient usuarios;
    private final AuditoriaPublisher auditoria;
    private final ZoneId zona;

    public SalonService(GrupoEsperaRepository espera, MesaRepository mesasRepo, MesaService mesas, UsuariosClient usuarios,
            AuditoriaPublisher auditoria, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.espera = espera;
        this.mesasRepo = mesasRepo;
        this.mesas = mesas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.zona = ZoneId.of(zona);
    }

    public ZoneId zona() {
        return zona;
    }

    // ------------------------------------------------------------------ HU-040 · lista de espera

    /** Grupos que esperan mesa, del que llegó primero al último (RN-10). */
    public List<GrupoEspera> espera() {
        return espera.findByEstadoOrderByLlegadaEnAsc(GrupoEspera.Estado.EN_ESPERA);
    }

    public List<GrupoResponse> esperaRespuesta() {
        Instant ahora = Instant.now();
        return espera().stream().map(g -> GrupoResponse.de(g, ahora)).toList();
    }

    public GrupoEspera grupo(String id) {
        return espera.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Grupo", id));
    }

    /** AC-040.1: el grupo queda al final de la lista con su hora de llegada. AC-040.2: nombre y personas obligatorios. */
    public GrupoEspera registrar(GrupoRequest req) {
        validar(req.nombre(), req.personas());
        GrupoEspera g = new GrupoEspera();
        g.setNombre(req.nombre().trim());
        g.setPersonas(req.personas());
        g.setLlegadaEn(horaLlegada(req.llegada()));
        g.setNotas(limpiar(req.notas()));
        g.setEstado(GrupoEspera.Estado.EN_ESPERA);
        g.setRegistradoPor(UsuarioActual.responsableActual());
        GrupoEspera guardado = espera.save(g);
        auditoria.registrar("REGISTRAR_ESPERA", "Grupo", guardado.getId(), guardado.getNombre() + " · " + guardado.getPersonas() + " personas");
        return guardado;
    }

    public GrupoEspera editar(String id, GrupoRequest req) {
        validar(req.nombre(), req.personas());
        GrupoEspera g = enEspera(id);
        g.setNombre(req.nombre().trim());
        g.setPersonas(req.personas());
        if (req.llegada() != null && !req.llegada().isBlank()) {
            g.setLlegadaEn(horaLlegada(req.llegada()));
        }
        g.setNotas(limpiar(req.notas()));
        GrupoEspera guardado = espera.save(g);
        auditoria.registrar("EDITAR_ESPERA", "Grupo", id, guardado.getNombre() + " · " + guardado.getPersonas() + " personas");
        return guardado;
    }

    /** El grupo se fue sin sentarse: sale de la lista pero no se borra, para la auditoría. */
    public GrupoEspera retirar(String id, String motivo) {
        GrupoEspera g = enEspera(id);
        g.setEstado(GrupoEspera.Estado.RETIRADO);
        g.setRetiradoEn(Instant.now());
        String m = limpiar(motivo);
        g.setMotivoRetiro(m == null ? "Retirado de la lista" : m);
        GrupoEspera guardado = espera.save(g);
        auditoria.registrar("RETIRAR_ESPERA", "Grupo", id, g.getNombre() + " · " + g.getMotivoRetiro());
        return guardado;
    }

    private GrupoEspera enEspera(String id) {
        GrupoEspera g = grupo(id);
        if (g.getEstado() != GrupoEspera.Estado.EN_ESPERA) {
            throw new ReglaNegocioException("El grupo " + g.getNombre() + " ya no está en la lista de espera");
        }
        return g;
    }

    private static void validar(String nombre, Integer personas) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("nombre: " + SalonDtos.MSG_NOMBRE);
        }
        if (personas == null || personas < 1) {
            throw new ReglaNegocioException("personas: " + SalonDtos.MSG_PERSONAS);
        }
        if (personas > 50) {
            throw new ReglaNegocioException("personas: Máximo 50 personas");
        }
    }

    /** Hora de hoy (HH:mm) en la zona del restaurante; vacía es «ahora». No puede ser futura. */
    Instant horaLlegada(String hhmm) {
        Instant ahora = Instant.now();
        if (hhmm == null || hhmm.isBlank()) {
            return ahora;
        }
        try {
            Instant llegada = LocalDate.now(zona).atTime(LocalTime.parse(hhmm.trim())).atZone(zona).toInstant();
            if (llegada.isAfter(ahora.plusSeconds(60))) {
                throw new ReglaNegocioException("llegada: La hora de llegada no puede ser posterior a la hora actual");
            }
            return llegada;
        } catch (DateTimeParseException ex) {
            throw new ReglaNegocioException("llegada: Hora en formato HH:mm");
        }
    }

    // ------------------------------------------------------------------ HU-041 / HU-046 · mapa

    /** AC-041.1 y AC-041.2: mesas activas (las inactivas no aparecen) con su estado, capacidad y totales. */
    public MapaSalon mapa() {
        List<MesaResponse> lista = mesas.tablero(false);
        return new MapaSalon(lista, contar(lista, Mesa.Estado.DISPONIBLE), contar(lista, Mesa.Estado.OCUPADA),
                contar(lista, Mesa.Estado.POR_LIMPIAR), contar(lista, Mesa.Estado.FUERA_DE_SERVICIO), espera().size(),
                huella());
    }

    private static long contar(List<MesaResponse> lista, Mesa.Estado estado) {
        return lista.stream().filter(m -> m.estado() == estado).count();
    }

    /**
     * HU-046 (NFR-WOK-004): resumen corto del salón. Cambia cuando cambia el estado, el grupo o el
     * mesero de alguna mesa activa, o la lista de espera. La tablet la consulta cada 3 s y solo pide
     * el mapa cuando cambia.
     */
    public String huella() {
        StringBuilder sb = new StringBuilder();
        for (Mesa m : mesasRepo.findByActivaTrueOrderByNumeroAsc()) {
            Mesa.Asignacion a = m.getAsignacion();
            sb.append(m.getId()).append(':').append(m.getEstado()).append(':').append(m.getCapacidad()).append(':')
                    .append(a == null ? "" : a.grupo() + "/" + a.mesero() + "/" + a.unionId()).append(':')
                    .append(m.getCuentaSolicitadaEn() != null).append(';');
        }
        sb.append('|');
        for (GrupoEspera g : espera()) {
            sb.append(g.getId()).append(':').append(g.getVersion()).append(';');
        }
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    // ------------------------------------------------------------------ HU-042 / HU-045 · asignar y unir

    public List<UsuariosClient.Mesero> meseros() {
        return usuarios.meseros();
    }

    /**
     * Sienta a un grupo en una o varias mesas libres con su mesero.
     * <ul>
     * <li>AC-042.1: las mesas pasan a OCUPADA, el grupo sale de la lista y el mesero las ve como suyas.</li>
     * <li>AC-042.2 y AC-045.1: los puestos de las mesas deben alcanzar para el grupo (CAPACIDAD_INSUFICIENTE).</li>
     * <li>AC-044.2 y AC-045.2: solo se asignan mesas libres (MESA_NO_DISPONIBLE).</li>
     * </ul>
     */
    public AsignacionResponse asignar(AsignarRequest req) {
        List<String> ids = req.mesaIds() == null ? List.of()
                : req.mesaIds().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
        if (ids.isEmpty()) {
            throw new ReglaNegocioException("Elige al menos una mesa");
        }
        if (ids.size() > MAX_MESAS_UNION) {
            throw new ReglaNegocioException("Se pueden unir hasta " + MAX_MESAS_UNION + " mesas");
        }
        if (req.mesero() == null || req.mesero().isBlank()) {
            throw new ReglaNegocioException("Elige el mesero que atenderá la mesa");
        }

        GrupoEspera grupo;
        if (req.grupoId() != null && !req.grupoId().isBlank()) {
            grupo = enEspera(req.grupoId());
        } else {
            // el grupo llega y encuentra mesa: se registra y se sienta en la misma operación (spec, acta 5)
            validar(req.nombre(), req.personas());
            grupo = new GrupoEspera();
            grupo.setId(new ObjectId().toHexString());
            grupo.setNombre(req.nombre().trim());
            grupo.setPersonas(req.personas());
            grupo.setLlegadaEn(Instant.now());
            grupo.setRegistradoPor(UsuarioActual.responsableActual());
        }

        List<Mesa> elegidas = ids.stream().map(mesas::obtener).sorted(Comparator.comparingInt(Mesa::getNumero)).toList();
        for (Mesa m : elegidas) {
            if (!m.isActiva() || m.getEstado() != Mesa.Estado.DISPONIBLE) {
                throw mesaNoDisponible(m);
            }
        }
        int puestos = elegidas.stream().mapToInt(Mesa::getCapacidad).sum();
        if (puestos < grupo.getPersonas()) {
            String cuales = elegidas.size() == 1 ? "La mesa " + elegidas.get(0).getNumero() + " tiene "
                    : "Las mesas " + numeros(elegidas) + " suman ";
            throw new ReglaNegocioException(CAPACIDAD_INSUFICIENTE, CAPACIDAD_INSUFICIENTE + " · " + cuales + puestos
                    + " puestos y el grupo es de " + grupo.getPersonas() + " personas");
        }
        UsuariosClient.Mesero mesero = usuarios.meseros().stream().filter(x -> x.username().equals(req.mesero().trim()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("El mesero " + req.mesero() + " no está activo; elige otro"));

        Instant ahora = Instant.now();
        boolean union = elegidas.size() > 1;
        List<Integer> nums = elegidas.stream().map(Mesa::getNumero).toList();
        Mesa.Asignacion asignacion = new Mesa.Asignacion(grupo.getId(), grupo.getNombre(), grupo.getPersonas(),
                mesero.username(), mesero.nombre(), ahora, union ? UUID.randomUUID().toString() : null,
                union ? nums : List.of());

        List<Mesa> ocupadas = new ArrayList<>();
        try {
            for (Mesa m : elegidas) {
                m.setEstado(Mesa.Estado.OCUPADA);
                m.setOcupadaDesde(ahora);
                m.setPorLimpiarDesde(null);
                m.setAsignacion(asignacion);
                ocupadas.add(mesasRepo.save(m));
            }
        } catch (OptimisticLockingFailureException ex) {
            // otra tablet cambió una de las mesas al mismo tiempo: se deshace lo ocupado (ADR-003, punto 7)
            for (Mesa m : ocupadas) {
                mesasRepo.findById(m.getId()).ifPresent(x -> {
                    x.setEstado(Mesa.Estado.DISPONIBLE);
                    x.setOcupadaDesde(null);
                    x.setAsignacion(null);
                    mesasRepo.save(x);
                });
            }
            throw new ReglaNegocioException(MESA_NO_DISPONIBLE, MESA_NO_DISPONIBLE
                    + " · Otra persona acaba de cambiar una de las mesas; revisa el mapa e intenta de nuevo");
        }

        grupo.setEstado(GrupoEspera.Estado.SENTADO);
        grupo.setMesaIds(elegidas.stream().map(Mesa::getId).toList());
        grupo.setMesaNumeros(nums);
        grupo.setMesero(mesero.username());
        grupo.setMeseroNombre(mesero.nombre());
        grupo.setSentadoEn(ahora);
        GrupoEspera sentado = espera.save(grupo);

        String detalle = grupo.getNombre() + " (" + grupo.getPersonas() + ") · mesa(s) " + numeros(elegidas) + " · "
                + mesero.nombre();
        auditoria.registrar("SENTAR_GRUPO", "Mesa", elegidas.get(0).getId(), detalle);
        if (union) {
            auditoria.registrar("UNIR_MESAS", "Mesa", elegidas.get(0).getId(), "Mesas " + numeros(elegidas) + " · "
                    + puestos + " puestos");
        }
        List<MesaResponse> respuesta = ocupadas.stream().map(m -> MesaService.respuesta(m, 0, BigDecimal.ZERO, 0)).toList();
        return new AsignacionResponse(GrupoResponse.de(sentado, ahora), respuesta, puestos, union);
    }

    private static ReglaNegocioException mesaNoDisponible(Mesa m) {
        String estado = !m.isActiva() ? "inactiva" : m.getEstado().etiqueta().toLowerCase();
        return new ReglaNegocioException(MESA_NO_DISPONIBLE, MESA_NO_DISPONIBLE + " · La mesa " + m.getNumero()
                + " está " + estado + "; solo se asignan mesas libres");
    }

    private static String numeros(List<Mesa> lista) {
        List<String> n = lista.stream().map(m -> String.valueOf(m.getNumero())).toList();
        return n.size() == 1 ? n.get(0) : String.join(", ", n.subList(0, n.size() - 1)) + " y " + n.get(n.size() - 1);
    }

    private static String limpiar(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
