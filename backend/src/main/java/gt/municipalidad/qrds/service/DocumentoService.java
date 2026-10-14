package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.DocumentoDtos.ArchivoRechazado;
import gt.municipalidad.qrds.dto.DocumentoDtos.ArchivoSubido;
import gt.municipalidad.qrds.dto.DocumentoDtos.CargaDocumentosRespuesta;
import gt.municipalidad.qrds.dto.DocumentoDtos.DocumentoResumen;
import gt.municipalidad.qrds.dto.DocumentoDtos.MensajeDocumento;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.DocumentoCaso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.OrigenDocumento;
import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.ResolucionCaso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.DocumentoCasoRepository;
import gt.municipalidad.qrds.util.Archivos;
import gt.municipalidad.qrds.util.Permisos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentoService {

    static final int MAX_ARCHIVOS_PUBLICOS = 5;

    private final CasoRepository casoRepository;
    private final DocumentoCasoRepository documentoCasoRepository;
    private final BitacoraCasoService bitacoraCasoService;
    private final CasoGestionService casoGestionService;
    private final NotificacionService notificacionService;
    private final ParametroService parametroService;
    private final Path directorio;
    private final boolean revisionContenido;

    public DocumentoService(
            CasoRepository casoRepository,
            DocumentoCasoRepository documentoCasoRepository,
            BitacoraCasoService bitacoraCasoService,
            CasoGestionService casoGestionService,
            NotificacionService notificacionService,
            ParametroService parametroService,
            @Value("${qrds.archivos.directorio}") String directorio,
            @Value("${qrds.documentos.revision-contenido:false}") boolean revisionContenido) {
        this.casoRepository = casoRepository;
        this.documentoCasoRepository = documentoCasoRepository;
        this.bitacoraCasoService = bitacoraCasoService;
        this.casoGestionService = casoGestionService;
        this.notificacionService = notificacionService;
        this.parametroService = parametroService;
        this.directorio = Path.of(directorio);
        this.revisionContenido = revisionContenido;
    }

    @Transactional
    public CargaDocumentosRespuesta adjuntarPublico(String codigo, List<MultipartFile> archivos, String ip) {
        Caso caso = casoPorCodigo(codigo);
        exigirEstado(caso, false, null, null);
        return guardarArchivos(caso, archivos, OrigenDocumento.CIUDADANO, null, ip, true, null);
    }

    @Transactional
    public CargaDocumentosRespuesta adjuntarInterno(
            Long casoId, List<MultipartFile> archivos, Usuario usuario, String ip, String justificacion) {
        Permisos.exigir(usuario, Permiso.DOCUMENTOS_CARGAR);
        Caso caso = casoVisible(casoId, usuario);
        exigirEstado(caso, true, usuario, justificacion);
        String nota = excepcion(caso, justificacion);
        return guardarArchivos(caso, archivos, OrigenDocumento.AGENTE, usuario, ip, false, nota);
    }

    @Transactional
    public void registrarReferencia(
            Caso caso,
            SeguimientoCaso seguimiento,
            ResolucionCaso resolucion,
            String nombre,
            String ruta,
            String mime,
            long tamanio,
            Usuario usuario,
            String ip,
            String descripcion) {
        DocumentoCaso documento = documentoCasoRepository.save(new DocumentoCaso(
                caso,
                nombre,
                ruta,
                mime,
                tamanio,
                OrigenDocumento.AGENTE,
                usuario));
        if (seguimiento != null) {
            documento.asociarSeguimiento(seguimiento);
        }
        if (resolucion != null) {
            documento.asociarResolucion(resolucion);
        }
        documentoCasoRepository.save(documento);
        bitacoraCasoService.registrar(caso, usuario, TipoEventoCaso.DOCUMENTO_CARGADO, descripcion, ip);
    }

    @Transactional(readOnly = true)
    public List<DocumentoResumen> listar(Long casoId, Usuario usuario) {
        Permisos.exigir(usuario, Permiso.CASOS_VER);
        Caso caso = casoVisible(casoId, usuario);
        return documentoCasoRepository.findActivosByCaso(caso).stream()
                .map(DocumentoResumen::de)
                .toList();
    }

    @Transactional
    public ResponseEntity<Resource> descargar(Long casoId, Long documentoId, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.DOCUMENTOS_DESCARGAR);
        Caso caso = casoPorId(casoId);
        if (!casoGestionService.puedeConsultar(usuario, caso)) {
            bitacoraCasoService.registrarDurable(
                    caso, usuario, TipoEventoCaso.INTENTO_DESCARGA_DENEGADO,
                    "Intento de descarga fuera del alcance del usuario.", ip);
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene permisos para consultar los documentos de este caso.");
        }
        DocumentoCaso documento = documentoCasoRepository.findByIdAndCaso(documentoId, caso)
                .filter(DocumentoCaso::isActivo)
                .orElse(null);
        if (documento == null) {
            bitacoraCasoService.registrarDurable(
                    caso, usuario, TipoEventoCaso.INTENTO_DESCARGA_DENEGADO,
                    "Intento de descarga con un identificador que no corresponde al caso.", ip);
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "DOCUMENTO_NO_ENCONTRADO",
                    "El documento solicitado no existe en este caso.");
        }
        Path ruta = Path.of(documento.getRutaArchivo()).toAbsolutePath().normalize();
        Path raiz = directorio.toAbsolutePath().normalize();
        if (!ruta.startsWith(raiz) || !Files.isRegularFile(ruta)) {
            bitacoraCasoService.registrarDurable(
                    caso,
                    usuario,
                    TipoEventoCaso.DOCUMENTO_FALTANTE,
                    "Falta el archivo de " + documento.getNombreArchivo() + " en el caso "
                            + caso.getCodigoSeguimiento() + ".",
                    ip);
            notificacionService.avisarDocumentoFaltante(caso, documento.getNombreArchivo());
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "DOCUMENTO_NO_ENCONTRADO",
                    "El documento no está disponible.");
        }
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.DOCUMENTO_DESCARGADO,
                "Descarga de documento " + documento.getNombreArchivo() + " del caso "
                        + caso.getCodigoSeguimiento() + ".",
                ip);
        String nombre = documento.getNombreArchivo().replace("\"", "").replace("\r", "").replace("\n", "");
        Resource recurso = new FileSystemResource(ruta);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(recurso);
    }

    @Transactional
    public MensajeDocumento darDeBaja(Long casoId, Long documentoId, Usuario usuario, String justificacion, String ip) {
        if (usuario.getRol() != Rol.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "Solo el administrador puede dar de baja un documento.");
        }
        String motivo = justificacion == null ? "" : justificacion.trim();
        if (motivo.length() < 10) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Indique la justificación de la baja.");
        }
        Caso caso = casoVisible(casoId, usuario);
        DocumentoCaso documento = documentoCasoRepository.findByIdAndCaso(documentoId, caso)
                .filter(DocumentoCaso::isActivo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DOCUMENTO_NO_ENCONTRADO",
                        "El documento solicitado no existe en este caso."));
        documento.darDeBaja(usuario, motivo);
        documentoCasoRepository.save(documento);
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.DOCUMENTO_BAJA,
                "Baja del documento " + documento.getNombreArchivo() + " en " + caso.getCodigoSeguimiento()
                        + ". Justificación: " + motivo,
                ip);
        return new MensajeDocumento("El documento quedó dado de baja. La evidencia almacenada se conserva.");
    }

    private CargaDocumentosRespuesta guardarArchivos(
            Caso caso,
            List<MultipartFile> archivos,
            OrigenDocumento origen,
            Usuario usuario,
            String ip,
            boolean limitarCantidad,
            String notaExcepcion) {
        List<ArchivoSubido> subidos = new ArrayList<>();
        List<ArchivoRechazado> rechazados = new ArrayList<>();
        if (archivos == null || archivos.isEmpty()) {
            return new CargaDocumentosRespuesta(subidos, rechazados);
        }
        long existentes = documentoCasoRepository.countActivos(caso);
        for (MultipartFile archivo : archivos) {
            if (archivo == null || archivo.isEmpty()) {
                continue;
            }
            String nombre = Archivos.nombreSeguro(archivo.getOriginalFilename());
            try {
                byte[] contenido = archivo.getBytes();
                String motivo = motivoDe(nombre, contenido, existentes + subidos.size(), limitarCantidad);
                if (motivo != null) {
                    if (revisionContenido && Archivos.pareceEjecutable(contenido)) {
                        Archivos.guardarCuarentena(directorio, contenido);
                        motivo = "El archivo no superó la revisión de contenido.";
                    }
                    rechazados.add(new ArchivoRechazado(nombre, motivo));
                    continue;
                }
                Path destino = Archivos.persistir(directorio, caso.getCodigoSeguimiento(), nombre, contenido);
                DocumentoCaso documento = documentoCasoRepository.save(new DocumentoCaso(
                        caso,
                        nombre,
                        destino.toString(),
                        Archivos.mime(nombre),
                        contenido.length,
                        origen,
                        usuario));
                subidos.add(ArchivoSubido.de(documento));
                String descripcion = "Carga de documento " + nombre + " (" + contenido.length + " bytes) en "
                        + caso.getCodigoSeguimiento() + ".";
                if (notaExcepcion != null) {
                    descripcion = descripcion + " " + notaExcepcion;
                }
                bitacoraCasoService.registrar(caso, usuario, TipoEventoCaso.DOCUMENTO_CARGADO, descripcion, ip);
            } catch (IOException ex) {
                rechazados.add(new ArchivoRechazado(nombre, "No fue posible almacenar el archivo."));
            }
        }
        return new CargaDocumentosRespuesta(subidos, rechazados);
    }

    private String motivoDe(String nombre, byte[] contenido, long total, boolean limitarCantidad) {
        int maximo = parametroService.maxArchivos();
        if (limitarCantidad && total >= maximo) {
            return "Máximo " + maximo + " archivos por caso.";
        }
        return Archivos.motivoRechazo(nombre, contenido, parametroService.maxBytes(), parametroService.extensiones());
    }

    private void exigirEstado(Caso caso, boolean interno, Usuario usuario, String justificacion) {
        EstadoCaso estado = caso.getEstado();
        if (estado == EstadoCaso.CERRADO || estado == EstadoCaso.ANULADO) {
            if (interno && usuario != null && usuario.getRol() == Rol.ADMIN && justificacionValida(justificacion)) {
                return;
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_HABILITADO",
                    "El caso no admite documentos en su estado actual.");
        }
        if (estado == EstadoCaso.RESUELTO && !interno) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_MODIFICABLE",
                    "El caso está resuelto y solo puede consultarse.");
        }
    }

    private String excepcion(Caso caso, String justificacion) {
        if (caso.getEstado() != EstadoCaso.CERRADO && caso.getEstado() != EstadoCaso.ANULADO) {
            return null;
        }
        return "Excepción autorizada: " + justificacion.trim();
    }

    private boolean justificacionValida(String justificacion) {
        return justificacion != null && justificacion.trim().length() >= 10;
    }

    private Caso casoVisible(Long casoId, Usuario usuario) {
        Caso caso = casoPorId(casoId);
        if (!casoGestionService.puedeConsultar(usuario, caso)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene permisos para consultar los documentos de este caso.");
        }
        return caso;
    }

    private Caso casoPorCodigo(String codigo) {
        return casoRepository.findByCodigoSeguimientoIgnoreCase(codigo == null ? "" : codigo.trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CASO_NO_ENCONTRADO",
                        "Código no encontrado. Verifique el número e intente nuevamente."));
    }

    private Caso casoPorId(Long id) {
        return casoRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "CASO_NO_ENCONTRADO",
                "El caso solicitado no existe."));
    }
}
