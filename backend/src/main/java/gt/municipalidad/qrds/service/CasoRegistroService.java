package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CasoPublicoDtos.RegistroCasoRequest;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.RegistroCasoRespuesta;
import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CasoRegistroService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<EstadoCaso> ESTADOS_CERRADOS = Set.of(EstadoCaso.CERRADO, EstadoCaso.ANULADO);

    private final CasoRepository casoRepository;
    private final AreaDependenciaRepository areaRepository;
    private final CaptchaService captchaService;
    private final CorrelativoService correlativoService;
    private final PlazoService plazoService;
    private final BitacoraCasoService bitacoraCasoService;
    private final NotificacionService notificacionService;

    public CasoRegistroService(
            CasoRepository casoRepository,
            AreaDependenciaRepository areaRepository,
            CaptchaService captchaService,
            CorrelativoService correlativoService,
            PlazoService plazoService,
            BitacoraCasoService bitacoraCasoService,
            NotificacionService notificacionService) {
        this.casoRepository = casoRepository;
        this.areaRepository = areaRepository;
        this.captchaService = captchaService;
        this.correlativoService = correlativoService;
        this.plazoService = plazoService;
        this.bitacoraCasoService = bitacoraCasoService;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public RegistroCasoRespuesta registrar(RegistroCasoRequest request, String ip) {
        captchaService.verificar(request.captchaId(), request.captchaRespuesta());

        boolean anonimo = request.esAnonimo();
        Map<String, String> errores = validar(request, anonimo);
        if (!errores.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    errores);
        }

        AreaDependencia area = resolverArea(request, anonimo);

        if (!anonimo && !request.forzarRegistro()) {
            detectarSimilar(request);
        }

        Instant ahora = Instant.now();
        Caso caso = new Caso();
        caso.setCodigoSeguimiento(correlativoService.siguiente(request.tipoCaso()));
        caso.setTipoCaso(request.tipoCaso());
        caso.setAreaDependencia(area);
        caso.setDescripcion(request.descripcion().trim());
        caso.setEsAnonimo(anonimo);
        caso.setAceptaPrivacidad(true);
        caso.setEstado(EstadoCaso.RECIBIDO);
        caso.setAvancePorcentaje(0);
        caso.setFechaRegistro(ahora);
        caso.setFechaUltimaActualizacion(ahora);
        caso.setFechaLimiteRespuesta(plazoService.fechaLimite(LocalDate.now(), request.tipoCaso().getDiasHabiles()));
        caso.setIpRegistro(ip);
        caso.setTelefono(anonimo ? null : digitos(request.telefono()));

        if (anonimo) {
            caso.setNombreCiudadano(null);
            caso.setEmailCiudadano(null);
            caso.setDenunciadoNombre(null);
        } else {
            caso.setNombreCiudadano(texto(request.nombreCiudadano()));
            caso.setEmailCiudadano(texto(request.email()).toLowerCase(Locale.ROOT));
            if (request.tipoCaso() == TipoCaso.D) {
                caso.setDenunciadoNombre(texto(request.denunciado()));
            }
        }

        casoRepository.save(caso);

        String correoBitacora = anonimo ? "anónimo" : caso.getEmailCiudadano();
        bitacoraCasoService.registrar(
                caso,
                null,
                TipoEventoCaso.CREACION,
                "Registro público de " + request.tipoCaso().getEtiqueta()
                        + " " + caso.getCodigoSeguimiento()
                        + ". Ciudadano: " + correoBitacora + ".",
                ip);

        boolean correoEnviado = notificacionService.enviarConfirmacionRegistro(caso);
        return RegistroCasoRespuesta.de(caso, correoEnviado);
    }

    private Map<String, String> validar(RegistroCasoRequest request, boolean anonimo) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (!request.aceptaPrivacidad()) {
            errores.put("aceptaPrivacidad", "Debe aceptar el aviso de privacidad para enviar el caso.");
        }
        if (!anonimo) {
            String nombre = texto(request.nombreCiudadano());
            if (nombre.length() < 2 || nombre.length() > 150) {
                errores.put("nombreCiudadano", "El nombre debe tener entre 2 y 150 caracteres.");
            }
            String email = texto(request.email());
            if (!EMAIL.matcher(email).matches()) {
                errores.put("email", "Ingrese un correo electrónico válido.");
            }
            if (texto(request.areaDependencia()).isEmpty()) {
                errores.put("areaDependencia", "Seleccione un área o dependencia.");
            }
        }
        String telefono = anonimo ? "" : digitos(request.telefono());
        if (!telefono.isEmpty() && (telefono.length() < 8 || telefono.length() > 15)) {
            errores.put("telefono", "El teléfono debe tener entre 8 y 15 dígitos.");
        }
        if (!anonimo && request.tipoCaso() == TipoCaso.D && texto(request.denunciado()).length() > 150) {
            errores.put("denunciado", "El nombre del denunciado no puede superar 150 caracteres.");
        }
        return errores;
    }

    private AreaDependencia resolverArea(RegistroCasoRequest request, boolean anonimo) {
        String codigo = texto(request.areaDependencia()).toUpperCase(Locale.ROOT);
        if (anonimo && codigo.isEmpty()) {
            return areaRepository.findByCodigo("ATENCION")
                    .filter(AreaDependencia::isActivo)
                    .or(() -> areaRepository.findByActivoTrueOrderByNombreAsc().stream().findFirst())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "VALIDACION",
                            "Datos inválidos. Verifique el formulario.",
                            Map.of("areaDependencia", "No hay un área disponible para el registro anónimo.")));
        }
        return areaRepository.findByCodigo(codigo)
                .filter(AreaDependencia::isActivo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDACION",
                        "Datos inválidos. Verifique el formulario.",
                        Map.of("areaDependencia", "Seleccione un área o dependencia.")));
    }

    private void detectarSimilar(RegistroCasoRequest request) {
        Instant desde = Instant.now().minus(24, ChronoUnit.HOURS);
        String email = texto(request.email()).toLowerCase(Locale.ROOT);
        String actual = normalizar(request.descripcion());
        List<Caso> recientes = casoRepository
                .findByEmailCiudadanoIgnoreCaseAndTipoCasoAndFechaRegistroAfterAndEstadoNotIn(
                        email, request.tipoCaso(), desde, ESTADOS_CERRADOS);
        for (Caso existente : recientes) {
            if (sonSimilares(actual, normalizar(existente.getDescripcion()))) {
                throw ApiException.casoSimilar(existente.getCodigoSeguimiento());
            }
        }
    }

    private boolean sonSimilares(String actual, String previo) {
        if (actual.equals(previo)) {
            return true;
        }
        String a = actual.length() > 80 ? actual.substring(0, 80) : actual;
        String b = previo.length() > 80 ? previo.substring(0, 80) : previo;
        return a.equals(b);
    }

    private String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String digitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }
}
