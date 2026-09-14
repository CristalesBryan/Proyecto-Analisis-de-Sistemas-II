package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CasoDtos.CasoDetalle;
import gt.municipalidad.qrds.dto.SeguimientoDtos.RegistroSeguimientoRespuesta;
import gt.municipalidad.qrds.dto.SeguimientoDtos.SeguimientoInterno;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.TipoSeguimiento;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.SeguimientoCasoRepository;
import gt.municipalidad.qrds.util.Archivos;
import gt.municipalidad.qrds.util.Permisos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class SeguimientoRegistroService {

    private final CasoGestionService casoGestionService;
    private final SeguimientoCasoRepository seguimientoCasoRepository;
    private final BitacoraCasoService bitacoraCasoService;
    private final NotificacionService notificacionService;
    private final Path directorio;

    public SeguimientoRegistroService(
            CasoGestionService casoGestionService,
            SeguimientoCasoRepository seguimientoCasoRepository,
            BitacoraCasoService bitacoraCasoService,
            NotificacionService notificacionService,
            @Value("${qrds.archivos.directorio}") String directorio) {
        this.casoGestionService = casoGestionService;
        this.seguimientoCasoRepository = seguimientoCasoRepository;
        this.bitacoraCasoService = bitacoraCasoService;
        this.notificacionService = notificacionService;
        this.directorio = Path.of(directorio);
    }

    @Transactional
    public RegistroSeguimientoRespuesta registrar(
            Long casoId,
            String tipoValor,
            String titulo,
            String descripcion,
            String porcentajeValor,
            String notificarValor,
            String padreValor,
            String justificacionExcepcional,
            MultipartFile archivo,
            Usuario usuario,
            String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_GESTIONAR);
        Caso caso = casoGestionService.localizarGestionable(casoId, usuario);
        boolean abierto = caso.getEstado().name().equals("EN_REVISION") || caso.getEstado().name().equals("EN_PROCESO");
        boolean excepcional = !abierto && usuario.getRol() == Rol.ADMIN;
        if (!abierto && !excepcional) {
            casoGestionService.exigirAbierto(caso);
        }
        if (excepcional && (justificacionExcepcional == null || justificacionExcepcional.trim().length() < 20)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    java.util.Map.of("justificacionExcepcional", "En casos finalizados justifique la nota interna (mín. 20 caracteres)."));
        }

        TipoSeguimiento tipo = parsearTipo(excepcional ? "INTERNA" : tipoValor);
        validarTexto(titulo, descripcion);
        Integer porcentaje = parsearPorcentaje(porcentajeValor);
        SeguimientoCaso padre = null;
        if (tipo == TipoSeguimiento.CORRECCION) {
            if (padreValor == null || padreValor.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDACION",
                        "Datos inválidos. Verifique el formulario.",
                        java.util.Map.of("seguimientoPadreId", "La corrección debe referenciar un seguimiento existente."));
            }
            try {
                padre = seguimientoCasoRepository.findByIdAndCaso(Long.parseLong(padreValor), caso)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "VALIDACION",
                                "Datos inválidos. Verifique el formulario.",
                                java.util.Map.of(
                                        "seguimientoPadreId",
                                        "La corrección debe referenciar un seguimiento existente.")));
            } catch (NumberFormatException ex) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDACION",
                        "Datos inválidos. Verifique el formulario.",
                        java.util.Map.of(
                                "seguimientoPadreId",
                                "La corrección debe referenciar un seguimiento existente."));
            }
        }

        String nombreArchivo = null;
        String rutaArchivo = null;
        String tipoMime = null;
        Long tamanio = null;
        if (archivo != null && !archivo.isEmpty()) {
            try {
                String nombre = Archivos.nombreSeguro(archivo.getOriginalFilename());
                byte[] contenido = archivo.getBytes();
                String motivo = Archivos.motivoRechazo(nombre, contenido);
                if (motivo != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "ARCHIVO_INVALIDO", motivo);
                }
                Path destino = Archivos.persistir(directorio, caso.getCodigoSeguimiento(), nombre, contenido);
                nombreArchivo = nombre;
                rutaArchivo = destino.toString();
                tipoMime = Archivos.mime(nombre);
                tamanio = (long) contenido.length;
            } catch (IOException ex) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ARCHIVO_INVALIDO",
                        "No fue posible almacenar el archivo.");
            }
        }

        boolean notificar = abierto
                && tipo == TipoSeguimiento.PUBLICA
                && !caso.isEsAnonimo()
                && Boolean.parseBoolean(notificarValor);
        SeguimientoCaso seguimiento = seguimientoCasoRepository.save(new SeguimientoCaso(
                caso,
                usuario,
                tipo,
                titulo.trim(),
                descripcion.trim(),
                porcentaje,
                padre,
                notificar,
                nombreArchivo,
                rutaArchivo,
                tipoMime,
                tamanio));
        if (porcentaje != null) {
            caso.setAvancePorcentaje(porcentaje);
        }
        caso.tocar();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.SEGUIMIENTO,
                "Seguimiento " + tipo.name() + " en " + caso.getCodigoSeguimiento() + ": " + titulo.trim()
                        + (excepcional ? ". Justificación: " + justificacionExcepcional.trim() : ""),
                ip);
        String aviso = null;
        if (notificar) {
            aviso = notificacionService.notificarSeguimientoPublico(caso, seguimiento)
                    ? null
                    : "Seguimiento guardado, pero no fue posible notificar al ciudadano.";
        }
        List<SeguimientoInterno> lista = seguimientoCasoRepository.findByCasoOrderByCreadoEnDesc(caso).stream()
                .map(SeguimientoInterno::de)
                .toList();
        CasoDetalle detalle = casoGestionService.detalleDe(caso, usuario);
        return new RegistroSeguimientoRespuesta(
                "Caso actualizado correctamente.",
                aviso,
                SeguimientoInterno.de(seguimiento),
                detalle,
                lista);
    }

    @Transactional
    public ResponseEntity<Resource> descargar(Long casoId, Long seguimientoId, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.DOCUMENTOS_DESCARGAR);
        Caso caso = casoGestionService.localizarVisible(casoId, usuario);
        SeguimientoCaso seguimiento = seguimientoCasoRepository.findByIdAndCaso(seguimientoId, caso)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DOCUMENTO_NO_ENCONTRADO",
                        "El documento solicitado no existe en este caso."));
        if (seguimiento.getRutaArchivo() == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "DOCUMENTO_NO_ENCONTRADO",
                    "El documento solicitado no existe en este caso.");
        }
        Path ruta = Path.of(seguimiento.getRutaArchivo()).toAbsolutePath().normalize();
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
                "Descarga de evidencia " + seguimiento.getNombreArchivo() + " del seguimiento "
                        + caso.getCodigoSeguimiento() + ".",
                ip);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(seguimiento.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + seguimiento.getNombreArchivo() + "\"")
                .body(new FileSystemResource(ruta));
    }

    private void validarTexto(String titulo, String descripcion) {
        if (titulo == null || titulo.trim().length() < 5 || titulo.trim().length() > 120) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    java.util.Map.of("titulo", "El título debe tener entre 5 y 120 caracteres."));
        }
        if (descripcion == null || descripcion.trim().length() < 20 || descripcion.trim().length() > 2000) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    java.util.Map.of("descripcion", "La descripción debe tener entre 20 y 2000 caracteres."));
        }
    }

    private TipoSeguimiento parsearTipo(String valor) {
        try {
            return TipoSeguimiento.valueOf(valor.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    java.util.Map.of("tipo", "Seleccione un tipo de seguimiento válido."));
        }
    }

    private Integer parsearPorcentaje(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero < 0 || numero > 100) {
                throw new NumberFormatException();
            }
            return numero;
        } catch (NumberFormatException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    java.util.Map.of("porcentajeAvance", "El porcentaje debe estar entre 0 y 100."));
        }
    }
}
