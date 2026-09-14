package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.DocumentoDtos.ArchivoRechazado;
import gt.municipalidad.qrds.dto.DocumentoDtos.ArchivoSubido;
import gt.municipalidad.qrds.dto.DocumentoDtos.CargaDocumentosRespuesta;
import gt.municipalidad.qrds.dto.DocumentoDtos.DocumentoResumen;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.DocumentoCaso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.OrigenDocumento;
import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.DocumentoCasoRepository;
import gt.municipalidad.qrds.util.Permisos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ARCHIVOS = 5;
    private static final Set<String> EXTENSIONES = Set.of("pdf", "jpg", "jpeg", "png", "docx");
    private static final Map<String, String> MIME = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final CasoRepository casoRepository;
    private final DocumentoCasoRepository documentoCasoRepository;
    private final BitacoraCasoService bitacoraCasoService;
    private final Path directorio;

    public DocumentoService(
            CasoRepository casoRepository,
            DocumentoCasoRepository documentoCasoRepository,
            BitacoraCasoService bitacoraCasoService,
            @Value("${qrds.archivos.directorio}") String directorio) {
        this.casoRepository = casoRepository;
        this.documentoCasoRepository = documentoCasoRepository;
        this.bitacoraCasoService = bitacoraCasoService;
        this.directorio = Path.of(directorio);
    }

    @Transactional
    public CargaDocumentosRespuesta adjuntarPublico(String codigo, List<MultipartFile> archivos, String ip) {
        Caso caso = casoPorCodigo(codigo);
        return guardarArchivos(caso, archivos, OrigenDocumento.CIUDADANO, null, ip, "Carga pública de documento ");
    }

    @Transactional
    public CargaDocumentosRespuesta adjuntarInterno(
            Long casoId, List<MultipartFile> archivos, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.DOCUMENTOS_CARGAR);
        Caso caso = casoPorId(casoId);
        return guardarArchivos(caso, archivos, OrigenDocumento.AGENTE, usuario, ip, "Carga interna de documento ");
    }

    @Transactional(readOnly = true)
    public List<DocumentoResumen> listar(Long casoId, Usuario usuario) {
        Permisos.exigir(usuario, Permiso.CASOS_VER);
        Caso caso = casoPorId(casoId);
        return documentoCasoRepository.findByCasoOrderBySubidoEnDesc(caso).stream()
                .map(DocumentoResumen::de)
                .toList();
    }

    @Transactional
    public ResponseEntity<Resource> descargar(Long casoId, Long documentoId, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.DOCUMENTOS_DESCARGAR);
        Caso caso = casoPorId(casoId);
        DocumentoCaso documento = documentoCasoRepository.findByIdAndCaso(documentoId, caso)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DOCUMENTO_NO_ENCONTRADO",
                        "El documento solicitado no existe en este caso."));
        Path ruta = Path.of(documento.getRutaArchivo()).toAbsolutePath().normalize();
        Path raiz = directorio.toAbsolutePath().normalize();
        if (!ruta.startsWith(raiz) || !Files.isRegularFile(ruta)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "DOCUMENTO_NO_ENCONTRADO",
                    "El documento solicitado no existe en este caso.");
        }
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.DOCUMENTO_DESCARGADO,
                "Descarga de documento " + documento.getNombreArchivo() + " del caso "
                        + caso.getCodigoSeguimiento() + ".",
                ip);
        Resource recurso = new FileSystemResource(ruta);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + documento.getNombreArchivo() + "\"")
                .body(recurso);
    }

    private CargaDocumentosRespuesta guardarArchivos(
            Caso caso,
            List<MultipartFile> archivos,
            OrigenDocumento origen,
            Usuario usuario,
            String ip,
            String prefijoBitacora) {
        if (caso.getEstado() == EstadoCaso.CERRADO || caso.getEstado() == EstadoCaso.ANULADO) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_HABILITADO",
                    "El caso no admite documentos en su estado actual.");
        }

        List<ArchivoSubido> subidos = new ArrayList<>();
        List<ArchivoRechazado> rechazados = new ArrayList<>();
        long existentes = documentoCasoRepository.countByCaso(caso);
        if (archivos == null || archivos.isEmpty()) {
            return new CargaDocumentosRespuesta(subidos, rechazados);
        }

        for (MultipartFile archivo : archivos) {
            if (archivo == null || archivo.isEmpty()) {
                continue;
            }
            String nombre = nombreSeguro(archivo.getOriginalFilename());
            try {
                byte[] contenido = archivo.getBytes();
                String motivo = validar(nombre, contenido, existentes + subidos.size());
                if (motivo != null) {
                    rechazados.add(new ArchivoRechazado(nombre, motivo));
                    continue;
                }
                Path destino = persistir(caso.getCodigoSeguimiento(), nombre, contenido);
                DocumentoCaso documento = documentoCasoRepository.save(new DocumentoCaso(
                        caso,
                        nombre,
                        destino.toString(),
                        MIME.get(extension(nombre)),
                        contenido.length,
                        origen,
                        usuario));
                subidos.add(ArchivoSubido.de(documento));
                bitacoraCasoService.registrar(
                        caso,
                        usuario,
                        TipoEventoCaso.DOCUMENTO_CARGADO,
                        prefijoBitacora + nombre + " en " + caso.getCodigoSeguimiento() + ".",
                        ip);
            } catch (IOException ex) {
                rechazados.add(new ArchivoRechazado(nombre, "No fue posible almacenar el archivo."));
            }
        }
        return new CargaDocumentosRespuesta(subidos, rechazados);
    }

    private String validar(String nombre, byte[] contenido, long total) {
        if (total >= MAX_ARCHIVOS) {
            return "Máximo 5 archivos por caso.";
        }
        String ext = extension(nombre);
        if (!EXTENSIONES.contains(ext)) {
            return "Formato no permitido. Use PDF, JPG, PNG o DOCX.";
        }
        if (contenido.length > MAX_BYTES) {
            return "El archivo supera el máximo de 5 MB.";
        }
        if (!firmaValida(contenido, ext)) {
            return "El contenido del archivo no coincide con el formato declarado.";
        }
        return null;
    }

    private boolean firmaValida(byte[] contenido, String ext) {
        if (contenido.length < 4) {
            return false;
        }
        return switch (ext) {
            case "pdf" -> contenido[0] == '%' && contenido[1] == 'P' && contenido[2] == 'D' && contenido[3] == 'F';
            case "jpg", "jpeg" -> (contenido[0] & 0xFF) == 0xFF && (contenido[1] & 0xFF) == 0xD8;
            case "png" -> (contenido[0] & 0xFF) == 0x89 && contenido[1] == 'P' && contenido[2] == 'N' && contenido[3] == 'G';
            case "docx" -> contenido[0] == 'P' && contenido[1] == 'K';
            default -> false;
        };
    }

    private Path persistir(String codigo, String nombre, byte[] contenido) throws IOException {
        Path carpeta = directorio.toAbsolutePath().normalize().resolve(codigo);
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(UUID.randomUUID() + "-" + nombre).normalize();
        if (!destino.startsWith(carpeta)) {
            throw new IOException("Ruta de archivo inválida.");
        }
        Files.write(destino, contenido);
        return destino;
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

    private String nombreSeguro(String original) {
        String nombre = original == null ? "archivo" : Path.of(original).getFileName().toString();
        nombre = nombre.replaceAll("[^a-zA-Z0-9._-]", "_");
        return nombre.isBlank() ? "archivo" : nombre;
    }

    private String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }
}
