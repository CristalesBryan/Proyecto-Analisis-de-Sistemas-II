package gt.municipalidad.qrds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.dto.ReporteDtos.CatalogoReporte;
import gt.municipalidad.qrds.dto.ReporteDtos.FiltrosReporteRequest;
import gt.municipalidad.qrds.dto.ReporteDtos.GenerarReporteRequest;
import gt.municipalidad.qrds.dto.ReporteDtos.ReporteGeneradoResponse;
import gt.municipalidad.qrds.dto.ReporteDtos.ReporteResumen;
import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.EstadoReporte;
import gt.municipalidad.qrds.entity.FormatoReporte;
import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.ReporteGenerado;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.entity.TipoReporte;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.ReporteGeneradoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import gt.municipalidad.qrds.service.ReporteConsultaService.AlcanceConsulta;
import gt.municipalidad.qrds.service.ReporteConsultaService.TablaReporte;
import gt.municipalidad.qrds.util.Permisos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReporteService {

    private static final String SIN_RESULTADOS = "No hay casos con los criterios seleccionados.";
    private static final String ARCHIVO_FALLIDO =
            "No fue posible generar el archivo del reporte. Los casos no fueron modificados.";

    private final ReporteGeneradoRepository reportes;
    private final ReporteConsultaService consulta;
    private final ReporteArchivoService archivos;
    private final ReporteJobWorker worker;
    private final BitacoraSistemaService bitacora;
    private final AreaDependenciaRepository areas;
    private final UsuarioRepository usuarios;
    private final NotificacionService notificaciones;
    private final ObjectMapper objectMapper;
    private final Path directorio;
    private final ParametroService parametroService;
    private final TransactionTemplate transaccionesNuevas;

    public ReporteService(
            ReporteGeneradoRepository reportes,
            ReporteConsultaService consulta,
            ReporteArchivoService archivos,
            ReporteJobWorker worker,
            BitacoraSistemaService bitacora,
            AreaDependenciaRepository areas,
            UsuarioRepository usuarios,
            NotificacionService notificaciones,
            ObjectMapper objectMapper,
            @Value("${qrds.reportes.directorio:./data/reportes}") String directorio,
            ParametroService parametroService,
            PlatformTransactionManager transacciones) {
        this.reportes = reportes;
        this.consulta = consulta;
        this.archivos = archivos;
        this.worker = worker;
        this.bitacora = bitacora;
        this.areas = areas;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
        this.objectMapper = objectMapper;
        this.directorio = Path.of(directorio);
        this.parametroService = parametroService;
        this.transaccionesNuevas = new TransactionTemplate(transacciones);
        this.transaccionesNuevas.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<CatalogoReporte> catalogo(Usuario usuario) {
        exigirAcceso(usuario);
        List<String> formatos = Arrays.stream(FormatoReporte.values()).map(Enum::name).toList();
        return Arrays.stream(TipoReporte.values())
                .map(tipo -> new CatalogoReporte(tipo.getCodigo(), tipo.getNombre(), tipo.getDescripcion(), formatos))
                .toList();
    }

    @Transactional
    public ReporteGeneradoResponse generar(GenerarReporteRequest request, Usuario usuario, String ip) {
        exigirAcceso(usuario);
        TipoReporte tipo = tipoDe(request.tipo());
        FiltrosReporteRequest filtros = request.filtros();
        validarFechas(filtros);
        AreaDependencia area = resolverArea(usuario, filtros.area(), ip);
        Long agenteId = resolverAgente(usuario, filtros.agenteId(), area);
        AlcanceConsulta alcance = new AlcanceConsulta(
                tipo,
                filtros.fechaDesde(),
                filtros.fechaHasta(),
                tipoCasoDe(filtros.tipoCaso()),
                estadoDe(filtros.estado()),
                area == null ? null : area.getId(),
                agenteId,
                Boolean.TRUE.equals(filtros.incluirAnulados()));
        String hash = hashDe(alcance, request.formato());
        ReporteGenerado existente = reportes
                .findFirstBySolicitadoPor_IdAndTipoReporteAndFormatoAndFiltrosHashAndEstadoAndExpiraEnAfterOrderByCreadoEnDesc(
                        usuario.getId(), tipo.getCodigo(), request.formato(), hash, EstadoReporte.LISTO, Instant.now())
                .orElse(null);
        if (existente != null) {
            return responder(existente, false, true, mensajeDe(existente));
        }

        long cantidad = consulta.contar(alcance);
        ReporteGenerado reporte = nuevo(usuario, tipo, request.formato(), filtros, hash);
        if (cantidad > parametroService.umbralReportes()) {
            reportes.save(reporte);
            Long id = reporte.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    worker.procesar(id);
                }
            });
            return responder(
                    reporte,
                    true,
                    false,
                    "El reporte quedó en cola. Podrá descargarlo cuando esté listo.");
        }
        try {
            completar(reporte, alcance, false);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            transaccionesNuevas.executeWithoutResult(status -> aplicarFallo(reporte, usuario, ip, ex));
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORTE_FALLIDO", ARCHIVO_FALLIDO);
        }
        return responder(reporte, false, false, mensajeDe(reporte));
    }

    @Transactional(readOnly = true)
    public List<ReporteResumen> misReportes(Usuario usuario) {
        exigirAcceso(usuario);
        return reportes.findBySolicitadoPor_IdOrderByCreadoEnDesc(usuario.getId()).stream()
                .map(this::resumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReporteGeneradoResponse obtener(Long id, Usuario usuario) {
        return responder(cargarPropio(id, usuario), false, false, null);
    }

    @Transactional
    public Descarga descargar(Long id, Usuario usuario, String ip) {
        ReporteGenerado reporte = cargarPropio(id, usuario);
        if (reporte.getEstado() == EstadoReporte.PENDIENTE || reporte.getEstado() == EstadoReporte.PROCESANDO) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REPORTE_PENDIENTE",
                    "El reporte todavía no está listo.");
        }
        if (reporte.getEstado() == EstadoReporte.FALLIDO) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORTE_FALLIDO", ARCHIVO_FALLIDO);
        }
        if (reporte.getFormato() == FormatoReporte.PANTALLA || reporte.getRutaArchivo() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REPORTE_SIN_ARCHIVO",
                    "Este reporte se consultó en pantalla y no tiene archivo para descargar.");
        }
        if (reporte.getExpiraEn() != null && reporte.getExpiraEn().isBefore(Instant.now())) {
            throw new ApiException(
                    HttpStatus.GONE,
                    "REPORTE_EXPIRADO",
                    "El reporte expiró. Genérelo nuevamente.");
        }
        Path ruta = Path.of(reporte.getRutaArchivo());
        if (!Files.exists(ruta)) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORTE_FALLIDO", ARCHIVO_FALLIDO);
        }
        bitacora.registrar(
                "REPORTE_DESCARGADO",
                usuario,
                reporte.getId(),
                "Descarga de " + reporte.getTipoReporte() + " en " + reporte.getFormato() + ".",
                ip,
                reporte.getFiltrosJson());
        String nombre = "reporte-" + reporte.getTipoReporte() + "." + extension(reporte.getFormato());
        return new Descarga(new FileSystemResource(ruta), nombre, reporte.getFormato());
    }

    @Transactional
    public void ejecutarJob(Long id) {
        ReporteGenerado reporte = reportes.findById(id).orElse(null);
        if (reporte == null || reporte.getEstado() != EstadoReporte.PENDIENTE) {
            return;
        }
        reporte.setEstado(EstadoReporte.PROCESANDO);
        reportes.save(reporte);
        try {
            AlcanceConsulta alcance = alcanceDe(reporte);
            completar(reporte, alcance, true);
            notificaciones.notificarReporteListo(reporte.getSolicitadoPor(), reporte.getTipoReporte());
        } catch (RuntimeException ex) {
            aplicarFallo(reporte, reporte.getSolicitadoPor(), null, ex);
        }
    }

    private void completar(ReporteGenerado reporte, AlcanceConsulta alcance, boolean asincrono) {
        TablaReporte tabla = consulta.consultar(alcance);
        reporte.setCantidadFilas(tabla.casos());
        reporte.setVistaPrevia(serializar(new Vista(tabla.columnas(), tabla.vistaPrevia())));
        if (reporte.getFormato() != FormatoReporte.PANTALLA) {
            String archivo = UUID.randomUUID() + "." + extension(reporte.getFormato());
            Path destino = directorio.resolve(archivo);
            try {
                String titulo = TipoReporte.deCodigo(reporte.getTipoReporte()).getNombre();
                if (reporte.getFormato() == FormatoReporte.PDF) {
                    archivos.escribirPdf(destino, titulo, tabla);
                } else {
                    archivos.escribirExcel(destino, titulo, tabla);
                }
            } catch (IOException ex) {
                throw new IllegalStateException(ARCHIVO_FALLIDO, ex);
            }
            reporte.setRutaArchivo(destino.toString());
        }
        Instant ahora = Instant.now();
        reporte.setEstado(EstadoReporte.LISTO);
        reporte.setFinalizadoEn(ahora);
        reporte.setExpiraEn(ahora.plus(parametroService.retencionReportes(), ChronoUnit.HOURS));
        reportes.save(reporte);
        String evento = reporte.getFormato() == FormatoReporte.PANTALLA ? "REPORTE_CONSULTADO" : "REPORTE_GENERADO";
        bitacora.registrar(
                evento,
                reporte.getSolicitadoPor(),
                reporte.getId(),
                eventoEtiqueta(evento) + " " + reporte.getTipoReporte() + ". Filas: " + reporte.getCantidadFilas() + ".",
                null,
                reporte.getFiltrosJson());
        if (asincrono) {
            return;
        }
    }

    private void aplicarFallo(ReporteGenerado reporte, Usuario usuario, String ip, RuntimeException ex) {
        reporte.setEstado(EstadoReporte.FALLIDO);
        reporte.setErrorMensaje(ARCHIVO_FALLIDO);
        reporte.setFinalizadoEn(Instant.now());
        if (reporte.getRutaArchivo() != null) {
            try {
                Files.deleteIfExists(Path.of(reporte.getRutaArchivo()));
            } catch (IOException ignored) {
                // El archivo parcial no debe impedir el registro del fallo.
            }
            reporte.setRutaArchivo(null);
        }
        reportes.save(reporte);
        bitacora.registrar(
                "REPORTE_FALLIDO",
                usuario,
                reporte.getId(),
                ARCHIVO_FALLIDO,
                ip,
                ex.getClass().getSimpleName());
    }

    private ReporteGenerado nuevo(
            Usuario usuario,
            TipoReporte tipo,
            FormatoReporte formato,
            FiltrosReporteRequest filtros,
            String hash) {
        ReporteGenerado reporte = new ReporteGenerado();
        reporte.setTipoReporte(tipo.getCodigo());
        reporte.setSolicitadoPor(usuario);
        reporte.setFormato(formato);
        reporte.setEstado(EstadoReporte.PENDIENTE);
        reporte.setFiltrosHash(hash);
        reporte.setFiltrosJson(serializar(filtros));
        reporte.setCreadoEn(Instant.now());
        return reporte;
    }

    private AreaDependencia resolverArea(Usuario usuario, String areaSolicitada, String ip) {
        if (usuario.getRol() == Rol.SUPERVISOR) {
            AreaDependencia propia = usuario.getAreaDependencia();
            if (propia == null) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PERMISO_DENEGADO",
                        "El supervisor no tiene un área asignada para consultar reportes.");
            }
            if (areaSolicitada != null
                    && !areaSolicitada.isBlank()
                    && !propia.getCodigo().equalsIgnoreCase(areaSolicitada.trim())) {
                bitacora.registrar(
                        "INTENTO_DENEGADO",
                        usuario,
                        null,
                        "Intento de generar un reporte de otra área.",
                        ip,
                        areaSolicitada);
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PERMISO_DENEGADO",
                        "No puede consultar información de otra área.");
            }
            return propia;
        }
        if (areaSolicitada == null || areaSolicitada.isBlank()) {
            return null;
        }
        return areas.findByCodigo(areaSolicitada.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "FILTROS_INVALIDOS",
                        "El área indicada no existe."));
    }

    private Long resolverAgente(Usuario usuario, Long agenteId, AreaDependencia area) {
        if (agenteId == null) {
            return null;
        }
        Usuario agente = usuarios.findById(agenteId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "FILTROS_INVALIDOS",
                        "El agente indicado no existe."));
        if (agente.getRol() != Rol.AGENTE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTROS_INVALIDOS", "El usuario indicado no es un agente.");
        }
        if (usuario.getRol() == Rol.SUPERVISOR) {
            AreaDependencia areaAgente = agente.getAreaDependencia();
            if (areaAgente == null || area == null || !area.getId().equals(areaAgente.getId())) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PERMISO_DENEGADO",
                        "No puede consultar agentes de otra área.");
            }
        }
        return agente.getId();
    }

    private void validarFechas(FiltrosReporteRequest filtros) {
        if (filtros.fechaHasta().isBefore(filtros.fechaDesde())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FILTROS_INVALIDOS",
                    "La fecha final no puede ser anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(filtros.fechaDesde(), filtros.fechaHasta()) > 365) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FILTROS_INVALIDOS",
                    "El rango de fechas no puede superar 365 días.");
        }
    }

    private TipoReporte tipoDe(String codigo) {
        TipoReporte tipo = TipoReporte.deCodigo(codigo);
        if (tipo == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FILTROS_INVALIDOS",
                    "Seleccione un tipo de reporte del catálogo.");
        }
        return tipo;
    }

    private TipoCaso tipoCasoDe(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        try {
            return TipoCaso.valueOf(codigo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTROS_INVALIDOS", "El tipo de caso no es válido.");
        }
    }

    private EstadoCaso estadoDe(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        try {
            return EstadoCaso.valueOf(codigo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTROS_INVALIDOS", "El estado indicado no es válido.");
        }
    }

    private ReporteGenerado cargarPropio(Long id, Usuario usuario) {
        exigirAcceso(usuario);
        ReporteGenerado reporte = reportes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El reporte no existe."));
        if (!reporte.getSolicitadoPor().getId().equals(usuario.getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No puede consultar un reporte de otro usuario.");
        }
        return reporte;
    }

    private void exigirAcceso(Usuario usuario) {
        Permisos.exigir(usuario, Permiso.REPORTES_GENERAR);
    }

    private AlcanceConsulta alcanceDe(ReporteGenerado reporte) {
        FiltrosReporteRequest filtros = leerFiltros(reporte.getFiltrosJson());
        AreaDependencia area = resolverArea(reporte.getSolicitadoPor(), filtros.area(), null);
        return new AlcanceConsulta(
                tipoDe(reporte.getTipoReporte()),
                filtros.fechaDesde(),
                filtros.fechaHasta(),
                tipoCasoDe(filtros.tipoCaso()),
                estadoDe(filtros.estado()),
                area == null ? null : area.getId(),
                filtros.agenteId(),
                Boolean.TRUE.equals(filtros.incluirAnulados()));
    }

    private ReporteGeneradoResponse responder(
            ReporteGenerado reporte, boolean asincrono, boolean reutilizado, String mensaje) {
        Vista vista = leerVista(reporte.getVistaPrevia());
        TipoReporte tipo = TipoReporte.deCodigo(reporte.getTipoReporte());
        String texto = mensaje == null ? mensajeDe(reporte) : mensaje;
        return new ReporteGeneradoResponse(
                reporte.getId(),
                reporte.getTipoReporte(),
                tipo == null ? reporte.getTipoReporte() : tipo.getNombre(),
                reporte.getFormato().name(),
                reporte.getEstado().name(),
                reporte.getCantidadFilas() == null ? 0 : reporte.getCantidadFilas(),
                asincrono,
                reutilizado,
                texto,
                vista.columnas(),
                vista.filas(),
                reporte.getCreadoEn(),
                reporte.getExpiraEn(),
                descargable(reporte));
    }

    private ReporteResumen resumen(ReporteGenerado reporte) {
        TipoReporte tipo = TipoReporte.deCodigo(reporte.getTipoReporte());
        return new ReporteResumen(
                reporte.getId(),
                reporte.getTipoReporte(),
                tipo == null ? reporte.getTipoReporte() : tipo.getNombre(),
                reporte.getFormato().name(),
                reporte.getEstado().name(),
                reporte.getCantidadFilas(),
                reporte.getCreadoEn(),
                reporte.getExpiraEn(),
                descargable(reporte),
                reporte.getErrorMensaje());
    }

    private boolean descargable(ReporteGenerado reporte) {
        return reporte.getEstado() == EstadoReporte.LISTO
                && reporte.getFormato() != FormatoReporte.PANTALLA
                && reporte.getRutaArchivo() != null
                && (reporte.getExpiraEn() == null || reporte.getExpiraEn().isAfter(Instant.now()));
    }

    private String mensajeDe(ReporteGenerado reporte) {
        if (reporte.getEstado() == EstadoReporte.FALLIDO) {
            return ARCHIVO_FALLIDO;
        }
        if (reporte.getCantidadFilas() != null && reporte.getCantidadFilas() == 0) {
            return SIN_RESULTADOS;
        }
        if (reporte.getEstado() == EstadoReporte.LISTO) {
            return "El reporte está listo.";
        }
        return "El reporte quedó en cola. Podrá descargarlo cuando esté listo.";
    }

    private String hashDe(AlcanceConsulta alcance, FormatoReporte formato) {
        String canonico = String.join("|",
                alcance.tipo().getCodigo(),
                formato.name(),
                alcance.desde().toString(),
                alcance.hasta().toString(),
                String.valueOf(alcance.tipoCaso()),
                String.valueOf(alcance.estado()),
                String.valueOf(alcance.areaId()),
                String.valueOf(alcance.agenteId()),
                String.valueOf(alcance.incluirAnulados()));
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonico.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte valor : bytes) {
                hex.append(String.format("%02x", valor));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String serializar(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private FiltrosReporteRequest leerFiltros(String json) {
        try {
            return objectMapper.readValue(json, FiltrosReporteRequest.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Vista leerVista(String json) {
        if (json == null || json.isBlank()) {
            return new Vista(List.of(), List.of());
        }
        try {
            return objectMapper.readValue(json, Vista.class);
        } catch (JsonProcessingException ex) {
            return new Vista(List.of(), List.of());
        }
    }

    private String extension(FormatoReporte formato) {
        return formato == FormatoReporte.PDF ? "pdf" : "xlsx";
    }

    private String eventoEtiqueta(String evento) {
        return "REPORTE_CONSULTADO".equals(evento) ? "Consulta de" : "Generación de";
    }

    public record Descarga(Resource recurso, String nombre, FormatoReporte formato) {
    }

    private record Vista(List<String> columnas, List<List<String>> filas) {
    }
}
