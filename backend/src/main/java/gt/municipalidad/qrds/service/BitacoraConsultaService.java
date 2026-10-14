package gt.municipalidad.qrds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.municipalidad.qrds.dto.BitacoraDtos.EventoBitacora;
import gt.municipalidad.qrds.dto.BitacoraDtos.FiltrosBitacora;
import gt.municipalidad.qrds.dto.BitacoraDtos.PaginaBitacora;
import gt.municipalidad.qrds.entity.BitacoraAcceso;
import gt.municipalidad.qrds.entity.BitacoraCaso;
import gt.municipalidad.qrds.entity.BitacoraSistema;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.TipoEventoAcceso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.BitacoraAccesoRepository;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import gt.municipalidad.qrds.repository.BitacoraSistemaRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BitacoraConsultaService {

    static final int MAX_DIAS = 90;
    static final int TAMANO_PAGINA = 50;
    static final int MAX_EXPORTACION = 2000;
    private static final int MAX_FILAS = 5000;
    private static final Set<TipoEventoAcceso> PUBLICOS = Set.of(
            TipoEventoAcceso.ACCESO_PORTAL,
            TipoEventoAcceso.CONSULTA_PUBLICA,
            TipoEventoAcceso.REGISTRO_CIUDADANO);
    private static final Set<TipoEventoCaso> DOCUMENTOS = Set.of(
            TipoEventoCaso.DOCUMENTO_CARGADO,
            TipoEventoCaso.DOCUMENTO_DESCARGADO);
    private static final Pattern SECRETO = Pattern.compile(
            "(?i)(password|contraseña|contrasena|token|jwt|bearer|secret)\\s*[:=]\\s*\\S+");
    private static final Pattern HASH = Pattern.compile("\\$2[aby]\\$\\d{2}\\$\\S+");
    private static final Set<String> CLAVES_SECRETAS = Set.of(
            "password", "contrasena", "contraseña", "token", "jwt", "bearer", "secret", "passwordhash");

    private final BitacoraAccesoRepository accesos;
    private final BitacoraCasoRepository casosBitacora;
    private final BitacoraSistemaRepository sistema;
    private final CasoRepository casos;
    private final UsuarioRepository usuarios;
    private final BitacoraSistemaService bitacora;
    private final BitacoraArchivoService archivos;
    private final ObjectMapper objectMapper;

    public BitacoraConsultaService(
            BitacoraAccesoRepository accesos,
            BitacoraCasoRepository casosBitacora,
            BitacoraSistemaRepository sistema,
            CasoRepository casos,
            UsuarioRepository usuarios,
            BitacoraSistemaService bitacora,
            BitacoraArchivoService archivos,
            ObjectMapper objectMapper) {
        this.accesos = accesos;
        this.casosBitacora = casosBitacora;
        this.sistema = sistema;
        this.casos = casos;
        this.usuarios = usuarios;
        this.bitacora = bitacora;
        this.archivos = archivos;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PaginaBitacora consultar(Usuario actorSesion, FiltrosBitacora filtros, int pagina, String ip) {
        Usuario actor = cargar(actorSesion.getId());
        Rango rango = validar(filtros);
        exigirAlcance(actor, filtros, ip);
        List<EventoBitacora> eventos = reunir(actor, filtros, rango);
        int size = TAMANO_PAGINA;
        int page = Math.max(pagina, 0);
        int desde = Math.min(page * size, eventos.size());
        int hasta = Math.min(desde + size, eventos.size());
        auditar(actor, "BITACORA_CONSULTADA", filtros, eventos.size(), null, ip,
                "Consulta de bitácora. Resultados: " + eventos.size() + ".");
        String mensaje = eventos.isEmpty() ? "No hay eventos con los criterios seleccionados." : null;
        return new PaginaBitacora(
                eventos.subList(desde, hasta),
                page,
                size,
                eventos.size(),
                eventos.isEmpty() ? 0 : (int) Math.ceil(eventos.size() / (double) size),
                mensaje);
    }

    @Transactional(readOnly = true)
    public EventoBitacora detalle(Usuario actorSesion, String dominio, Long id) {
        Usuario actor = cargar(actorSesion.getId());
        EventoBitacora evento = buscarUno(dominio, id);
        if (evento == null || !visible(actor, evento)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El evento no existe.");
        }
        return evento;
    }

    @Transactional(readOnly = true)
    public Archivo exportar(Usuario actorSesion, FiltrosBitacora filtros, String formato, String ip) {
        Usuario actor = cargar(actorSesion.getId());
        Rango rango = validar(filtros);
        exigirAlcance(actor, filtros, ip);
        List<EventoBitacora> eventos = reunir(actor, filtros, rango);
        if (eventos.size() > MAX_EXPORTACION) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EXPORTACION_GRANDE",
                    "El volumen es alto. Reduzca el rango de fechas.");
        }
        String normalizado = formato == null ? "" : formato.trim().toUpperCase(Locale.ROOT);
        if (!normalizado.equals("PDF") && !normalizado.equals("XLSX")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", "El formato debe ser PDF o Excel.");
        }
        String titulo = "Generado por " + actor.getNombre() + " (" + actor.getEmail() + ") el " + LocalDate.now(ZoneOffset.UTC);
        String resumen = resumenFiltros(filtros);
        try {
            byte[] contenido = normalizado.equals("PDF")
                    ? archivos.pdf(titulo, resumen, eventos)
                    : archivos.excel(titulo, resumen, eventos);
            auditar(actor, "BITACORA_EXPORTADA", filtros, eventos.size(), normalizado, ip,
                    "Exportación de bitácora generada y entregada en " + normalizado + ".");
            String nombre = "auditoria-" + rango.desde() + "-" + rango.hasta() + (normalizado.equals("PDF") ? ".pdf" : ".xlsx");
            MediaType tipo = normalizado.equals("PDF")
                    ? MediaType.APPLICATION_PDF
                    : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            return new Archivo(nombre, tipo, contenido);
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXPORTACION_FALLIDA",
                    "No fue posible generar el archivo. Las bitácoras no fueron modificadas.");
        }
    }

    private List<EventoBitacora> reunir(Usuario actor, FiltrosBitacora filtros, Rango rango) {
        String dominio = normalizar(filtros.dominio());
        List<EventoBitacora> eventos = new ArrayList<>();
        if (dominio.isEmpty() || dominio.equals("ACCESO") || dominio.equals("PUBLICO")) {
            accesos.findAll(accesoSpec(actor, filtros, rango, dominio), pagina()).forEach(evento -> eventos.add(deAcceso(evento)));
        }
        if (dominio.isEmpty() || dominio.equals("CASO") || dominio.equals("DOCUMENTO")) {
            casosBitacora.findAll(casoSpec(actor, filtros, rango, dominio), pagina())
                    .forEach(evento -> eventos.add(deCaso(evento)));
        }
        if (dominio.isEmpty() || esDominioSistema(dominio)) {
            sistema.findAll(sistemaSpec(actor, filtros, rango, dominio), pagina())
                    .forEach(evento -> eventos.add(deSistema(evento)));
        }
        eventos.sort(Comparator.comparing(EventoBitacora::fechaHora, Comparator.nullsLast(Comparator.reverseOrder())));
        return eventos;
    }

    private Specification<BitacoraAcceso> accesoSpec(Usuario actor, FiltrosBitacora filtros, Rango rango, String dominio) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), rango.inicio()));
            predicados.add(cb.lessThan(root.get("fechaHora"), rango.fin()));
            if ("PUBLICO".equals(dominio)) {
                predicados.add(root.get("tipoEvento").in(PUBLICOS));
            } else if ("ACCESO".equals(dominio)) {
                predicados.add(cb.not(root.get("tipoEvento").in(PUBLICOS)));
            }
            TipoEventoAcceso tipo = tipoAcceso(filtros.tipo());
            if (filtros.tipo() != null && !filtros.tipo().isBlank() && tipo == null) {
                return cb.disjunction();
            }
            if (tipo != null) {
                predicados.add(cb.equal(root.get("tipoEvento"), tipo));
            }
            if (filtros.usuarioId() != null) {
                predicados.add(cb.equal(root.get("usuario").get("id"), filtros.usuarioId()));
            }
            if (filtros.ip() != null && !filtros.ip().isBlank()) {
                predicados.add(cb.like(cb.lower(root.get("ipCliente")), "%" + filtros.ip().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (filtros.resultado() != null && !filtros.resultado().isBlank()) {
                predicados.add(cb.equal(cb.lower(root.get("resultado")), filtros.resultado().trim().toLowerCase(Locale.ROOT)));
            }
            if (filtros.caso() != null && !filtros.caso().isBlank()) {
                return cb.disjunction();
            }
            if (actor.getRol() == Rol.SUPERVISOR) {
                predicados.add(cb.not(root.get("tipoEvento").in(PUBLICOS)));
                predicados.add(cb.equal(root.get("usuario").get("areaDependencia").get("id"), actor.getAreaDependencia().getId()));
                predicados.add(cb.or(
                        cb.equal(root.get("usuario").get("rol"), Rol.AGENTE),
                        cb.equal(root.get("usuario").get("id"), actor.getId())));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private Specification<BitacoraCaso> casoSpec(Usuario actor, FiltrosBitacora filtros, Rango rango, String dominio) {
        return (root, query, cb) -> {
            if (filtros.resultado() != null && !filtros.resultado().isBlank()) {
                return cb.disjunction();
            }
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), rango.inicio()));
            predicados.add(cb.lessThan(root.get("fechaHora"), rango.fin()));
            if ("DOCUMENTO".equals(dominio)) {
                predicados.add(root.get("tipoEvento").in(DOCUMENTOS));
            }
            TipoEventoCaso tipo = tipoCaso(filtros.tipo());
            if (filtros.tipo() != null && !filtros.tipo().isBlank() && tipo == null) {
                return cb.disjunction();
            }
            if (tipo != null) {
                predicados.add(cb.equal(root.get("tipoEvento"), tipo));
            }
            if (filtros.usuarioId() != null) {
                predicados.add(cb.equal(root.get("usuario").get("id"), filtros.usuarioId()));
            }
            if (filtros.caso() != null && !filtros.caso().isBlank()) {
                predicados.add(cb.equal(cb.lower(root.get("caso").get("codigoSeguimiento")), filtros.caso().trim().toLowerCase(Locale.ROOT)));
            }
            if (filtros.ip() != null && !filtros.ip().isBlank()) {
                predicados.add(cb.like(cb.lower(root.get("ipCliente")), "%" + filtros.ip().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (actor.getRol() == Rol.SUPERVISOR) {
                predicados.add(cb.equal(root.get("caso").get("areaDependencia").get("id"), actor.getAreaDependencia().getId()));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private Specification<BitacoraSistema> sistemaSpec(Usuario actor, FiltrosBitacora filtros, Rango rango, String dominio) {
        return (root, query, cb) -> {
            if (filtros.resultado() != null && !filtros.resultado().isBlank()) {
                return cb.disjunction();
            }
            if (filtros.caso() != null && !filtros.caso().isBlank()) {
                return cb.disjunction();
            }
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), rango.inicio()));
            predicados.add(cb.lessThan(root.get("fechaHora"), rango.fin()));
            String dominioTabla = dominioTabla(dominio);
            if (dominioTabla != null) {
                predicados.add(cb.equal(root.get("dominio"), dominioTabla));
            }
            if (filtros.tipo() != null && !filtros.tipo().isBlank()) {
                predicados.add(cb.equal(cb.upper(root.get("tipoEvento")), filtros.tipo().trim().toUpperCase(Locale.ROOT)));
            }
            if (filtros.usuarioId() != null) {
                predicados.add(cb.equal(root.get("usuario").get("id"), filtros.usuarioId()));
            }
            if (filtros.ip() != null && !filtros.ip().isBlank()) {
                predicados.add(cb.like(cb.lower(root.get("ipCliente")), "%" + filtros.ip().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (actor.getRol() == Rol.SUPERVISOR) {
                predicados.add(cb.equal(root.get("usuario").get("areaDependencia").get("id"), actor.getAreaDependencia().getId()));
                predicados.add(cb.or(
                        cb.equal(root.get("usuario").get("rol"), Rol.AGENTE),
                        cb.equal(root.get("usuario").get("id"), actor.getId())));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private EventoBitacora buscarUno(String dominio, Long id) {
        String normalizado = normalizar(dominio);
        if ("ACCESO".equals(normalizado) || "PUBLICO".equals(normalizado)) {
            return accesos.findById(id).map(this::deAcceso).orElse(null);
        }
        if ("CASO".equals(normalizado)) {
            return casosBitacora.findById(id).map(this::deCaso).orElse(null);
        }
        if ("SYS".equals(normalizado)) {
            return sistema.findById(id).map(this::deSistema).orElse(null);
        }
        return null;
    }

    private boolean visible(Usuario actor, EventoBitacora evento) {
        if (actor.getRol() != Rol.SUPERVISOR) {
            return true;
        }
        if ("PUBLICO".equals(evento.dominio())) {
            return false;
        }
        if (actor.getAreaDependencia() == null) {
            return false;
        }
        if ("CASO".equals(evento.dominio()) || "DOCUMENTO".equals(evento.dominio())) {
            if (evento.casoId() == null) {
                return false;
            }
            Caso caso = casos.findById(evento.casoId()).orElse(null);
            return caso != null
                    && caso.getAreaDependencia() != null
                    && actor.getAreaDependencia().getId().equals(caso.getAreaDependencia().getId());
        }
        if (evento.usuarioId() == null) {
            return false;
        }
        if (evento.usuarioId().equals(actor.getId())) {
            return true;
        }
        Usuario dueno = usuarios.findById(evento.usuarioId()).orElse(null);
        return dueno != null
                && dueno.getRol() == Rol.AGENTE
                && dueno.getAreaDependencia() != null
                && actor.getAreaDependencia().getId().equals(dueno.getAreaDependencia().getId());
    }

    private void exigirAlcance(Usuario actor, FiltrosBitacora filtros, String ip) {
        if (actor.getRol() != Rol.SUPERVISOR) {
            return;
        }
        if (actor.getAreaDependencia() == null) {
            denegar(actor, ip, "Supervisor sin área intentó consultar bitácoras.");
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISO_DENEGADO", "Su usuario no tiene un área asignada.");
        }
        if (filtros.caso() != null && !filtros.caso().isBlank()) {
            Caso caso = casos.findByCodigoSeguimientoIgnoreCase(filtros.caso().trim()).orElse(null);
            if (caso != null && (caso.getAreaDependencia() == null
                    || !actor.getAreaDependencia().getId().equals(caso.getAreaDependencia().getId()))) {
                denegar(actor, ip, "Intento de consultar la bitácora del caso " + caso.getCodigoSeguimiento() + ".");
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PERMISO_DENEGADO",
                        "No puede consultar eventos fuera de su área.");
            }
        }
        if (filtros.usuarioId() != null) {
            Usuario objetivo = usuarios.findById(filtros.usuarioId()).orElse(null);
            boolean propio = objetivo != null && objetivo.getId().equals(actor.getId());
            boolean agenteDeArea = objetivo != null
                    && objetivo.getRol() == Rol.AGENTE
                    && objetivo.getAreaDependencia() != null
                    && actor.getAreaDependencia().getId().equals(objetivo.getAreaDependencia().getId());
            if (objetivo != null && !propio && !agenteDeArea) {
                denegar(actor, ip, "Intento de consultar eventos del usuario " + objetivo.getId() + ".");
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PERMISO_DENEGADO",
                        "No puede consultar eventos fuera de su área.");
            }
        }
    }

    private EventoBitacora deAcceso(BitacoraAcceso evento) {
        boolean publico = PUBLICOS.contains(evento.getTipoEvento());
        Usuario usuario = evento.getUsuario();
        return new EventoBitacora(
                (publico ? "PUBLICO:" : "ACCESO:") + evento.getId(),
                publico ? "PUBLICO" : "ACCESO",
                evento.getId(),
                evento.getFechaHora(),
                evento.getTipoEvento().name(),
                usuario == null ? "Público" : usuario.getNombre(),
                usuario == null ? null : usuario.getId(),
                null,
                null,
                limpiar(evento.getDetalle()),
                null,
                null,
                evento.getIpCliente(),
                evento.getResultado(),
                limpiar(evento.getUserAgent()));
    }

    private EventoBitacora deCaso(BitacoraCaso evento) {
        boolean documento = DOCUMENTOS.contains(evento.getTipoEvento());
        Usuario usuario = evento.getUsuario();
        Caso caso = evento.getCaso();
        return new EventoBitacora(
                "CASO:" + evento.getId(),
                documento ? "DOCUMENTO" : "CASO",
                evento.getId(),
                evento.getFechaHora(),
                evento.getTipoEvento().name(),
                usuario == null ? "Sistema" : usuario.getNombre(),
                usuario == null ? null : usuario.getId(),
                caso == null ? null : caso.getCodigoSeguimiento(),
                caso == null ? null : caso.getId(),
                limpiar(evento.getDescripcion()),
                evento.getEstadoAnterior(),
                evento.getEstadoNuevo(),
                evento.getIpCliente(),
                null,
                null);
    }

    private EventoBitacora deSistema(BitacoraSistema evento) {
        String dominio = dominioVisible(evento.getDominio());
        Usuario usuario = evento.getUsuario();
        return new EventoBitacora(
                "SYS:" + evento.getId(),
                dominio,
                evento.getId(),
                evento.getFechaHora(),
                evento.getTipoEvento(),
                usuario == null ? "Sistema" : usuario.getNombre(),
                usuario == null ? null : usuario.getId(),
                null,
                null,
                limpiar(evento.getDescripcion()),
                null,
                null,
                evento.getIpCliente(),
                null,
                null);
    }

    private void auditar(
            Usuario actor,
            String tipo,
            FiltrosBitacora filtros,
            int cantidad,
            String formato,
            String ip,
            String descripcion) {
        String metadata = "{"
                + "\"meta\":true,"
                + "\"desde\":\"" + texto(filtros.desde()) + "\","
                + "\"hasta\":\"" + texto(filtros.hasta()) + "\","
                + "\"dominio\":\"" + escapar(normalizar(filtros.dominio())) + "\","
                + "\"tipo\":\"" + escapar(normalizar(filtros.tipo())) + "\","
                + "\"cantidad\":" + cantidad
                + (formato == null ? "" : ",\"formato\":\"" + formato + "\"")
                + "}";
        bitacora.registrarNueva("AUDITORIA", "BITACORA", tipo, actor, null, descripcion, ip, metadata);
    }

    private void denegar(Usuario actor, String ip, String descripcion) {
        bitacora.registrarNueva(
                "AUDITORIA",
                "BITACORA",
                "INTENTO_DENEGADO",
                actor,
                null,
                descripcion,
                ip,
                "{\"meta\":true,\"resultado\":\"DENEGADO\"}");
    }

    private Rango validar(FiltrosBitacora filtros) {
        if (filtros == null || filtros.desde() == null || filtros.hasta() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTROS_INVALIDOS", "Reduzca el rango de fechas.");
        }
        long dias = ChronoUnit.DAYS.between(filtros.desde(), filtros.hasta());
        if (dias < 0 || dias > MAX_DIAS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTROS_INVALIDOS", "Reduzca el rango de fechas.");
        }
        return new Rango(
                filtros.desde(),
                filtros.hasta(),
                filtros.desde().atStartOfDay().toInstant(ZoneOffset.UTC),
                filtros.hasta().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private PageRequest pagina() {
        return PageRequest.of(0, MAX_FILAS, Sort.by(Sort.Direction.DESC, "fechaHora"));
    }

    private Usuario cargar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "TOKEN_INVALIDO",
                "Su sesión ha expirado. Por favor inicie sesión nuevamente."));
    }

    private TipoEventoAcceso tipoAcceso(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return TipoEventoAcceso.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private TipoEventoCaso tipoCaso(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return TipoEventoCaso.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean esDominioSistema(String dominio) {
        return dominio.isEmpty()
                || dominio.equals("USUARIO")
                || dominio.equals("REPORTE")
                || dominio.equals("DOCUMENTO")
                || dominio.equals("NOTIFICACION")
                || dominio.equals("AUDITORIA")
                || dominio.equals("PARAMETROS");
    }

    private String dominioTabla(String dominio) {
        return switch (dominio) {
            case "USUARIO" -> "USUARIOS";
            case "REPORTE" -> "REPORTES";
            case "DOCUMENTO" -> "DOCUMENTOS";
            case "NOTIFICACION" -> "NOTIFICACIONES";
            case "AUDITORIA" -> "AUDITORIA";
            case "PARAMETROS" -> "PARAMETROS";
            default -> null;
        };
    }

    private String dominioVisible(String dominio) {
        if (dominio == null) {
            return "SISTEMA";
        }
        return switch (dominio) {
            case "USUARIOS" -> "USUARIO";
            case "REPORTES" -> "REPORTE";
            case "DOCUMENTOS" -> "DOCUMENTO";
            case "NOTIFICACIONES" -> "NOTIFICACION";
            default -> dominio;
        };
    }

    private String limpiar(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }
        String texto = SECRETO.matcher(valor).replaceAll("$1: [omitido]");
        texto = HASH.matcher(texto).replaceAll("[omitido]");
        return limpiarJson(texto);
    }

    private String limpiarJson(String valor) {
        try {
            JsonNode nodo = objectMapper.readTree(valor);
            if (nodo instanceof ObjectNode objeto) {
                List<String> quitar = new ArrayList<>();
                objeto.fieldNames().forEachRemaining(nombre -> {
                    if (CLAVES_SECRETAS.contains(nombre.toLowerCase(Locale.ROOT))) {
                        quitar.add(nombre);
                    }
                });
                quitar.forEach(objeto::remove);
                return objectMapper.writeValueAsString(objeto);
            }
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return valor;
        }
        return valor;
    }

    private String resumenFiltros(FiltrosBitacora filtros) {
        return "Filtros: desde " + filtros.desde()
                + ", hasta " + filtros.hasta()
                + ", ámbito " + vacio(filtros.dominio())
                + ", tipo " + vacio(filtros.tipo())
                + ", caso " + vacio(filtros.caso());
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private String vacio(String valor) {
        return valor == null || valor.isBlank() ? "todos" : valor.trim();
    }

    private String texto(LocalDate fecha) {
        return fecha == null ? "" : fecha.toString();
    }

    private String escapar(String valor) {
        return valor.replace("\\", "").replace("\"", "");
    }

    public record Archivo(String nombre, MediaType tipo, byte[] contenido) {
    }

    private record Rango(LocalDate desde, LocalDate hasta, Instant inicio, Instant fin) {
    }
}
