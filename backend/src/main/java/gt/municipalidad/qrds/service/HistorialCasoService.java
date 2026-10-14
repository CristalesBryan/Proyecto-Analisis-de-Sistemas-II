package gt.municipalidad.qrds.service;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.SeguimientoPublico;
import gt.municipalidad.qrds.dto.HistorialDtos.EventoHistorial;
import gt.municipalidad.qrds.dto.HistorialDtos.PaginaHistorial;
import gt.municipalidad.qrds.dto.HistorialDtos.VistaCiudadano;
import gt.municipalidad.qrds.entity.BitacoraCaso;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.DocumentoCaso;
import gt.municipalidad.qrds.entity.ObservacionCaso;
import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.ResolucionCaso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.TipoSeguimiento;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.DocumentoCasoRepository;
import gt.municipalidad.qrds.repository.ObservacionCasoRepository;
import gt.municipalidad.qrds.repository.ResolucionCasoRepository;
import gt.municipalidad.qrds.repository.SeguimientoCasoRepository;
import gt.municipalidad.qrds.util.Permisos;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistorialCasoService {

    static final int TAMANO_PAGINA = 100;
    private static final String MARCA_INTERNA = "Uso interno — no distribuir al ciudadano.";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Guatemala"));
    private static final Set<String> CATEGORIAS = Set.of(
            "ESTADOS", "SEGUIMIENTOS", "DOCUMENTOS", "ASIGNACIONES", "RESOLUCION");
    private static final Comparator<EventoHistorial> RECIENTE_PRIMERO = Comparator
            .comparing(EventoHistorial::fechaHora, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(EventoHistorial::id, Comparator.reverseOrder());

    private final CasoRepository casoRepository;
    private final CasoGestionService casoGestionService;
    private final BitacoraCasoRepository bitacoraCasoRepository;
    private final SeguimientoCasoRepository seguimientoCasoRepository;
    private final ObservacionCasoRepository observacionCasoRepository;
    private final DocumentoCasoRepository documentoCasoRepository;
    private final ResolucionCasoRepository resolucionCasoRepository;
    private final BitacoraCasoService bitacoraCasoService;

    public HistorialCasoService(
            CasoRepository casoRepository,
            CasoGestionService casoGestionService,
            BitacoraCasoRepository bitacoraCasoRepository,
            SeguimientoCasoRepository seguimientoCasoRepository,
            ObservacionCasoRepository observacionCasoRepository,
            DocumentoCasoRepository documentoCasoRepository,
            ResolucionCasoRepository resolucionCasoRepository,
            BitacoraCasoService bitacoraCasoService) {
        this.casoRepository = casoRepository;
        this.casoGestionService = casoGestionService;
        this.bitacoraCasoRepository = bitacoraCasoRepository;
        this.seguimientoCasoRepository = seguimientoCasoRepository;
        this.observacionCasoRepository = observacionCasoRepository;
        this.documentoCasoRepository = documentoCasoRepository;
        this.resolucionCasoRepository = resolucionCasoRepository;
        this.bitacoraCasoService = bitacoraCasoService;
    }

    @Transactional(readOnly = true)
    public PaginaHistorial consultar(Long casoId, Usuario usuario, String categoria, String texto, int page, int size) {
        Caso caso = casoVisible(casoId, usuario);
        String filtro = categoriaNormalizada(categoria);
        List<EventoHistorial> eventos = new ArrayList<>();
        boolean parcial = reunir(caso, eventos);
        List<EventoHistorial> visibles = eventos.stream()
                .filter(evento -> filtro == null || filtro.equals(evento.categoria()))
                .filter(evento -> coincide(evento, texto))
                .sorted(RECIENTE_PRIMERO)
                .toList();
        int tamano = size < 1 ? TAMANO_PAGINA : Math.min(size, TAMANO_PAGINA);
        int pagina = Math.max(page, 0);
        int desde = pagina * tamano;
        List<EventoHistorial> corte = desde >= visibles.size()
                ? List.of()
                : visibles.subList(desde, Math.min(desde + tamano, visibles.size()));
        String aviso = parcial
                ? "La vista es parcial: no fue posible leer una de las fuentes. No se agregaron eventos de esa fuente."
                : null;
        return new PaginaHistorial(corte, pagina, tamano, visibles.size(), parcial, aviso);
    }

    @Transactional(readOnly = true)
    public VistaCiudadano vistaCiudadano(Long casoId, Usuario usuario) {
        if (usuario.getRol() != Rol.ADMIN && usuario.getRol() != Rol.SUPERVISOR) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene permisos para consultar la vista ciudadana.");
        }
        Caso caso = casoVisible(casoId, usuario);
        List<SeguimientoPublico> seguimientos;
        try {
            seguimientos = seguimientoCasoRepository
                    .findByCasoAndTipoOrderByCreadoEnDesc(caso, TipoSeguimiento.PUBLICA)
                    .stream()
                    .map(SeguimientoPublico::de)
                    .toList();
        } catch (RuntimeException ex) {
            seguimientos = List.of();
        }
        return new VistaCiudadano(
                caso.getCodigoSeguimiento(),
                caso.getTipoCaso().getEtiqueta(),
                caso.getEstado().name(),
                caso.getFechaRegistro(),
                caso.getFechaUltimaActualizacion(),
                caso.getAvancePorcentaje(),
                seguimientos);
    }

    @Transactional
    public ResponseEntity<byte[]> exportar(Long casoId, Usuario usuario, String categoria, String texto, String ip) {
        Caso caso = casoVisible(casoId, usuario);
        List<EventoHistorial> eventos = new ArrayList<>();
        reunir(caso, eventos);
        String filtro = categoriaNormalizada(categoria);
        List<EventoHistorial> visibles = eventos.stream()
                .filter(evento -> filtro == null || filtro.equals(evento.categoria()))
                .filter(evento -> coincide(evento, texto))
                .sorted(RECIENTE_PRIMERO)
                .toList();
        byte[] pdf = pdf(caso, visibles);
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.HISTORIAL_EXPORTADO,
                "Exportación de historial de uso interno del caso " + caso.getCodigoSeguimiento() + ".",
                ip);
        String nombre = "historial-" + caso.getCodigoSeguimiento() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(pdf);
    }

    private boolean reunir(Caso caso, List<EventoHistorial> destino) {
        boolean parcial = false;
        List<BitacoraCaso> bitacora = new ArrayList<>();
        try {
            bitacora.addAll(bitacoraCasoRepository.findByCasoOrderByFechaHoraDesc(caso));
        } catch (RuntimeException ex) {
            parcial = true;
        }
        List<SeguimientoCaso> seguimientos = new ArrayList<>();
        try {
            seguimientos.addAll(seguimientoCasoRepository.findByCasoOrderByCreadoEnDesc(caso));
        } catch (RuntimeException ex) {
            parcial = true;
        }
        List<ObservacionCaso> observaciones = new ArrayList<>();
        try {
            observaciones.addAll(observacionCasoRepository.findByCasoOrderByCreadoEnDesc(caso));
        } catch (RuntimeException ex) {
            parcial = true;
        }
        List<DocumentoCaso> documentos = new ArrayList<>();
        try {
            documentos.addAll(documentoCasoRepository.findActivosByCaso(caso));
        } catch (RuntimeException ex) {
            parcial = true;
        }
        ResolucionCaso resolucion = null;
        try {
            resolucion = resolucionCasoRepository.findByCasoIdAndVigenteTrue(caso.getId()).orElse(null);
        } catch (RuntimeException ex) {
            parcial = true;
        }

        boolean hayCreacion = bitacora.stream().anyMatch(item -> item.getTipoEvento() == TipoEventoCaso.CREACION);
        boolean hayAsignacion = bitacora.stream().anyMatch(item ->
                item.getTipoEvento() == TipoEventoCaso.ASIGNACION
                        || item.getTipoEvento() == TipoEventoCaso.REASIGNACION);
        boolean hayResolucion = bitacora.stream().anyMatch(item -> item.getTipoEvento() == TipoEventoCaso.RESOLUCION);

        if (!hayCreacion) {
            String resumen = caso.isEsAnonimo()
                    ? "Registro del caso. Ciudadano anónimo."
                    : "Registro del caso.";
            destino.add(evento(
                    -2,
                    "CREACION",
                    "ESTADOS",
                    "Registro del caso",
                    resumen,
                    resumen,
                    caso.getFechaRegistro(),
                    "Sistema",
                    false,
                    null));
        }
        if (!hayAsignacion && caso.getAgenteAsignado() != null) {
            String resumen = "Asignado a " + caso.getAgenteAsignado().getNombre() + ".";
            destino.add(evento(
                    -1,
                    "ASIGNACION",
                    "ASIGNACIONES",
                    "Asignación",
                    resumen,
                    resumen,
                    caso.getFechaRegistro(),
                    "Sistema",
                    true,
                    null));
        }
        for (SeguimientoCaso seguimiento : seguimientos) {
            boolean publico = seguimiento.getTipo() == TipoSeguimiento.PUBLICA;
            String detalle = seguimiento.getTitulo() + ". " + seguimiento.getDescripcion();
            destino.add(evento(
                    clave(3, seguimiento.getId()),
                    publico ? "SEGUIMIENTO_PUBLICO" : "SEGUIMIENTO_INTERNO",
                    "SEGUIMIENTOS",
                    publico ? "Seguimiento público" : "Seguimiento interno",
                    seguimiento.getTitulo(),
                    limpio(detalle),
                    seguimiento.getCreadoEn(),
                    autor(seguimiento.getUsuario()),
                    !publico,
                    null));
            if (publico && seguimiento.isNotificado()) {
                destino.add(evento(
                        clave(7, seguimiento.getId()),
                        "NOTIFICACION",
                        "NOTIFICACIONES",
                        "Notificación del caso",
                        "Notificación de seguimiento: enviada.",
                        "Notificación de seguimiento «" + seguimiento.getTitulo() + "»: enviada.",
                        seguimiento.getCreadoEn(),
                        "Sistema",
                        true,
                        null));
            }
        }
        for (ObservacionCaso observacion : observaciones) {
            destino.add(evento(
                    clave(5, observacion.getId()),
                    "OBSERVACION",
                    "OTROS",
                    "Observación interna",
                    limpio(observacion.getTexto()),
                    limpio(observacion.getTexto()),
                    observacion.getCreadoEn(),
                    autor(observacion.getUsuario()),
                    true,
                    null));
        }
        for (DocumentoCaso documento : documentos) {
            if (!documento.isActivo()) {
                continue;
            }
            String resumen = documento.getNombreArchivo() + " · " + formatoTamano(documento.getTamanioBytes());
            destino.add(evento(
                    clave(4, documento.getId()),
                    "DOCUMENTO_CARGADO",
                    "DOCUMENTOS",
                    "Documento cargado",
                    resumen,
                    resumen,
                    documento.getSubidoEn(),
                    autor(documento.getSubidoPor()),
                    true,
                    documento.getId()));
        }
        if (!hayResolucion && resolucion != null) {
            String resumen = "Resultado: " + resolucion.getTipoResultado().getEtiqueta() + ".";
            destino.add(evento(
                    clave(6, resolucion.getId()),
                    "RESOLUCION",
                    "RESOLUCION",
                    "Resolución",
                    resumen,
                    limpio(resumen + " " + resolucion.getComentario()),
                    resolucion.getCreadoEn(),
                    autor(resolucion.getUsuario()),
                    true,
                    null));
        }
        if (resolucion != null && resolucion.getEstadoNotificacion() != null) {
            String estado = resolucion.getEstadoNotificacion().toLowerCase(Locale.ROOT);
            destino.add(evento(
                    clave(8, resolucion.getId()),
                    "NOTIFICACION",
                    "NOTIFICACIONES",
                    "Notificación del caso",
                    "Notificación de resolución: " + estado + ".",
                    "Notificación de resolución: " + estado + ".",
                    resolucion.getCreadoEn(),
                    "Sistema",
                    true,
                    null));
        }
        for (BitacoraCaso item : bitacora) {
            if (omitirBitacora(item)) {
                continue;
            }
            String texto = limpio(item.getDescripcion());
            destino.add(evento(
                    item.getId(),
                    item.getTipoEvento().name(),
                    categoriaDe(item.getTipoEvento()),
                    tituloDe(item.getTipoEvento()),
                    texto,
                    texto,
                    item.getFechaHora(),
                    autor(item.getUsuario()),
                    interno(item),
                    null));
        }
        return parcial;
    }

    private boolean omitirBitacora(BitacoraCaso item) {
        TipoEventoCaso tipo = item.getTipoEvento();
        return tipo == TipoEventoCaso.SEGUIMIENTO
                || tipo == TipoEventoCaso.OBSERVACION
                || tipo == TipoEventoCaso.DOCUMENTO_CARGADO
                || tipo == TipoEventoCaso.HISTORIAL_EXPORTADO;
    }

    private String categoriaDe(TipoEventoCaso tipo) {
        return switch (tipo) {
            case CREACION, CAMBIO_ESTADO, CIERRE, ANULACION, ESCALAMIENTO, PRORROGA -> "ESTADOS";
            case ASIGNACION, REASIGNACION -> "ASIGNACIONES";
            case RESOLUCION -> "RESOLUCION";
            case DOCUMENTO_DESCARGADO, DOCUMENTO_BAJA, DOCUMENTO_FALTANTE, INTENTO_DESCARGA_DENEGADO -> "DOCUMENTOS";
            default -> "OTROS";
        };
    }

    private String tituloDe(TipoEventoCaso tipo) {
        return switch (tipo) {
            case CREACION -> "Registro del caso";
            case ASIGNACION -> "Asignación";
            case REASIGNACION -> "Reasignación";
            case CAMBIO_ESTADO -> "Cambio de estado";
            case CIERRE -> "Cierre";
            case ANULACION -> "Anulación";
            case RESOLUCION -> "Resolución";
            case PRORROGA -> "Prórroga";
            case ESCALAMIENTO -> "Escalamiento";
            case MODIFICACION -> "Modificación";
            case DOCUMENTO_DESCARGADO -> "Documento descargado";
            case DOCUMENTO_BAJA -> "Documento dado de baja";
            case DOCUMENTO_FALTANTE -> "Documento faltante";
            case INTENTO_DESCARGA_DENEGADO -> "Descarga denegada";
            case CONSULTA_SEGUIMIENTO -> "Consulta de seguimiento";
            default -> tipo.name().replace('_', ' ');
        };
    }

    private boolean interno(BitacoraCaso item) {
        if (item.getTipoEvento() == TipoEventoCaso.CREACION) {
            String texto = item.getDescripcion() == null ? "" : item.getDescripcion();
            return texto.contains("@");
        }
        return true;
    }

    private EventoHistorial evento(
            long id,
            String tipo,
            String categoria,
            String titulo,
            String resumen,
            String detalle,
            Instant fecha,
            String autor,
            boolean interno,
            Long documentoId) {
        return new EventoHistorial(id, tipo, categoria, titulo, resumen, detalle, fecha, autor, interno, documentoId);
    }

    private long clave(int origen, Long id) {
        long valor = id == null ? 0 : id;
        return -((valor * 10) + origen);
    }

    private Caso casoVisible(Long casoId, Usuario usuario) {
        Permisos.exigir(usuario, Permiso.CASOS_VER);
        Caso caso = casoRepository.findById(casoId).orElse(null);
        if (caso == null || !casoGestionService.puedeConsultar(usuario, caso)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene permisos para consultar este caso.");
        }
        return caso;
    }

    private String categoriaNormalizada(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return null;
        }
        String valor = categoria.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIAS.contains(valor)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTRO_INVALIDO", "El tipo de evento no es válido.");
        }
        return valor;
    }

    private boolean coincide(EventoHistorial evento, String texto) {
        if (texto == null || texto.isBlank()) {
            return true;
        }
        String busqueda = texto.trim().toLowerCase(Locale.ROOT);
        return (evento.titulo() + " " + evento.resumen() + " " + evento.detalle())
                .toLowerCase(Locale.ROOT)
                .contains(busqueda);
    }

    private String autor(Usuario usuario) {
        if (usuario == null || usuario.getNombre() == null || usuario.getNombre().isBlank()) {
            return "Sistema";
        }
        return usuario.getNombre();
    }

    private String limpio(String texto) {
        if (texto == null) {
            return "";
        }
        return texto
                .replaceAll("(?i)[a-z]:\\\\[^\\s]+", "")
                .replaceAll("/[^\\s]*documentos[^\\s]*", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String formatoTamano(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return (bytes / 1024) + " KB";
    }

    private byte[] pdf(Caso caso, List<EventoHistorial> eventos) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document documento = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            writer.setCompressionLevel(0);
            documento.open();
            documento.add(new Paragraph(
                    "Municipalidad — Historial del caso " + caso.getCodigoSeguimiento(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            documento.add(new Paragraph(MARCA_INTERNA, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            documento.add(new Paragraph(
                    "Estado: " + caso.getEstado().name(),
                    FontFactory.getFont(FontFactory.HELVETICA, 10)));
            documento.add(new Paragraph(" "));
            if (eventos.isEmpty()) {
                documento.add(new Paragraph("El expediente no tiene eventos adicionales al registro."));
            }
            for (EventoHistorial evento : eventos) {
                String cuando = evento.fechaHora() == null ? "" : FECHA.format(evento.fechaHora());
                documento.add(new Paragraph(
                        cuando + " · " + evento.titulo() + (evento.interno() ? " · Interno" : " · Público"),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                documento.add(new Paragraph(
                        evento.autor() + " — " + evento.detalle(),
                        FontFactory.getFont(FontFactory.HELVETICA, 9)));
                documento.add(new Paragraph(" "));
            }
            documento.close();
            return salida.toByteArray();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXPORTACION_NO_DISPONIBLE",
                    "No fue posible exportar el historial.");
        }
    }
}
