package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CasoDtos.AccionCasoRespuesta;
import gt.municipalidad.qrds.dto.CasoDtos.AgenteOpcion;
import gt.municipalidad.qrds.dto.CasoDtos.AnularRequest;
import gt.municipalidad.qrds.dto.CasoDtos.AsignarRequest;
import gt.municipalidad.qrds.dto.CasoDtos.CerrarCasoRequest;
import gt.municipalidad.qrds.dto.CasoDtos.CambioEstadoRequest;
import gt.municipalidad.qrds.dto.CasoDtos.CasoDetalle;
import gt.municipalidad.qrds.dto.CasoDtos.EscalarRequest;
import gt.municipalidad.qrds.dto.CasoDtos.ModificarCasoRequest;
import gt.municipalidad.qrds.dto.CasoDtos.ObservacionRequest;
import gt.municipalidad.qrds.dto.CasoDtos.PaginaCasos;
import gt.municipalidad.qrds.dto.CasoDtos.ProrrogaRequest;
import gt.municipalidad.qrds.dto.CasoDtos.ReasignarRequest;
import gt.municipalidad.qrds.dto.DocumentoDtos.DocumentoResumen;
import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.ObservacionCaso;
import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.Prioridad;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.CasoSpecifications;
import gt.municipalidad.qrds.repository.DocumentoCasoRepository;
import gt.municipalidad.qrds.repository.ObservacionCasoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import gt.municipalidad.qrds.util.Permisos;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CasoGestionService {

    private static final Map<String, String> ORDEN = Map.of(
            "codigo", "codigoSeguimiento",
            "tipo", "tipoCaso",
            "area", "areaDependencia.nombre",
            "estado", "estado",
            "fechaRegistro", "fechaRegistro");

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CasoRepository casoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AreaDependenciaRepository areaDependenciaRepository;
    private final DocumentoCasoRepository documentoCasoRepository;
    private final ObservacionCasoRepository observacionCasoRepository;
    private final BitacoraCasoRepository bitacoraCasoRepository;
    private final BitacoraCasoService bitacoraCasoService;
    private final PlazoService plazoService;
    private final NotificacionService notificacionService;

    public CasoGestionService(
            CasoRepository casoRepository,
            UsuarioRepository usuarioRepository,
            AreaDependenciaRepository areaDependenciaRepository,
            DocumentoCasoRepository documentoCasoRepository,
            ObservacionCasoRepository observacionCasoRepository,
            BitacoraCasoRepository bitacoraCasoRepository,
            BitacoraCasoService bitacoraCasoService,
            PlazoService plazoService,
            NotificacionService notificacionService) {
        this.casoRepository = casoRepository;
        this.usuarioRepository = usuarioRepository;
        this.areaDependenciaRepository = areaDependenciaRepository;
        this.documentoCasoRepository = documentoCasoRepository;
        this.observacionCasoRepository = observacionCasoRepository;
        this.bitacoraCasoRepository = bitacoraCasoRepository;
        this.bitacoraCasoService = bitacoraCasoService;
        this.plazoService = plazoService;
        this.notificacionService = notificacionService;
    }

    @Transactional(readOnly = true)
    public PaginaCasos listar(
            Usuario usuario,
            String estado,
            String tipo,
            String area,
            String codigo,
            LocalDate desde,
            LocalDate hasta,
            Boolean sinAsignar,
            String orden,
            String direccion,
            int page,
            int size) {
        Permisos.exigir(usuario, Permiso.CASOS_VER);
        int pagina = Math.max(page, 1);
        int tamano = size <= 0 ? 20 : Math.min(size, 100);
        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(direccion) ? Sort.Direction.ASC : Sort.Direction.DESC,
                ORDEN.getOrDefault(orden == null ? "" : orden, "fechaRegistro"));
        Specification<Caso> spec = CasoSpecifications.visiblePara(usuario)
                .and(CasoSpecifications.conFiltros(estado, tipo, area, codigo, desde, hasta, sinAsignar));
        try {
            Page<Caso> resultado = casoRepository.findAll(spec, PageRequest.of(pagina - 1, tamano, sort));
            return new PaginaCasos(
                    resultado.getContent().stream().map(caso -> CasoMapper.resumen(caso, plazoService)).toList(),
                    pagina,
                    tamano,
                    resultado.getTotalElements(),
                    resultado.getTotalPages());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTRO_INVALIDO", "Los criterios de búsqueda no son válidos.");
        }
    }

    @Transactional(readOnly = true)
    public CasoDetalle detalle(Long casoId, Usuario usuario) {
        Permisos.exigir(usuario, Permiso.CASOS_VER);
        Caso caso = localizarVisible(casoId, usuario);
        return detalleDe(caso, usuario);
    }

    @Transactional(readOnly = true)
    public List<AgenteOpcion> agentes(Usuario usuario, String area) {
        Permisos.exigir(usuario, Permiso.CASOS_ASIGNAR);
        String codigo = area == null || area.isBlank()
                ? (usuario.getRol() == Rol.ADMIN
                        ? null
                        : usuario.getAreaDependencia() == null ? "" : usuario.getAreaDependencia().getCodigo())
                : area.trim().toUpperCase();
        if (usuario.getRol() == Rol.SUPERVISOR
                && usuario.getAreaDependencia() != null
                && codigo != null
                && !codigo.equals(usuario.getAreaDependencia().getCodigo())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
        List<Usuario> agentes = codigo == null
                ? usuarioRepository.findByRolAndActivoTrueOrderByNombreAsc(Rol.AGENTE)
                : usuarioRepository.findByRolAndActivoTrueAndAreaDependencia_CodigoOrderByNombreAsc(Rol.AGENTE, codigo);
        return agentes.stream().map(CasoMapper::agente).toList();
    }

    @Transactional
    public AccionCasoRespuesta asignar(Long casoId, AsignarRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_ASIGNAR);
        Caso caso = localizarVisible(casoId, usuario);
        exigirNoFinalizado(caso);
        if (caso.getAgenteAsignado() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_YA_ASIGNADO",
                    "El caso ya tiene un agente. Use la reasignación.");
        }
        Usuario agente = agenteDelArea(request.agenteId(), caso);
        EstadoCaso anterior = caso.getEstado();
        caso.setAgenteAsignado(agente);
        if (anterior == EstadoCaso.RECIBIDO) {
            caso.setEstado(EstadoCaso.EN_REVISION);
        }
        caso.tocar();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.ASIGNACION,
                "Caso " + caso.getCodigoSeguimiento() + " asignado a " + agente.getNombre() + ".",
                ip,
                anterior,
                caso.getEstado());
        notificacionService.notificarAgenteAsignado(caso, agente);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta reasignar(Long casoId, ReasignarRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_ASIGNAR);
        Caso caso = localizarVisible(casoId, usuario);
        exigirNoFinalizado(caso);
        if (caso.getAgenteAsignado() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_DISPONIBLE",
                    "El caso seleccionado ya no se encuentra disponible para la operación.");
        }
        Usuario anteriorAgente = caso.getAgenteAsignado();
        Usuario agente = agenteDelArea(request.agenteId(), caso);
        if (anteriorAgente.getId().equals(agente.getId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AGENTE_INVALIDO",
                    "Seleccione un agente distinto al asignado actualmente.");
        }
        caso.setAgenteAsignado(agente);
        caso.tocar();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.REASIGNACION,
                "Reasignación de " + anteriorAgente.getNombre() + " a " + agente.getNombre()
                        + ". Motivo: " + request.motivo().trim(),
                ip,
                caso.getEstado(),
                caso.getEstado());
        notificacionService.notificarAgenteAsignado(caso, agente);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta cambiarEstado(Long casoId, CambioEstadoRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_GESTIONAR);
        Caso caso = localizarGestionable(casoId, usuario);
        exigirNoFinalizado(caso);
        EstadoCaso destino = parsearEstado(request.nuevoEstado());
        if (destino == EstadoCaso.ANULADO || destino == EstadoCaso.CERRADO) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSICION_INVALIDA",
                    "Transición de estado no permitida. Consulte el ciclo de vida del caso.");
        }
        EstadoCaso anterior = caso.getEstado();
        if (!anterior.transiciones().contains(destino)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSICION_INVALIDA",
                    "Transición de estado no permitida. Consulte el ciclo de vida del caso.");
        }
        caso.setEstado(destino);
        caso.tocar();
        observacionCasoRepository.save(new ObservacionCaso(caso, usuario, request.observacion().trim()));
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.CAMBIO_ESTADO,
                "Cambio de estado " + anterior.name() + " → " + destino.name() + ".",
                ip,
                anterior,
                destino);
        notificacionService.notificarCambioEstado(caso, anterior, destino);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta agregarObservacion(Long casoId, ObservacionRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_GESTIONAR);
        Caso caso = localizarGestionable(casoId, usuario);
        exigirNoFinalizado(caso);
        observacionCasoRepository.save(new ObservacionCaso(caso, usuario, request.texto().trim()));
        caso.tocar();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.OBSERVACION,
                "Observación interna registrada en " + caso.getCodigoSeguimiento() + ".",
                ip);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta anular(Long casoId, AnularRequest request, Usuario usuario, String ip) {
        if (usuario.getRol() != Rol.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
        Caso caso = localizarVisible(casoId, usuario);
        exigirNoFinalizado(caso);
        if (!caso.getEstado().transiciones().contains(EstadoCaso.ANULADO)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSICION_INVALIDA",
                    "Transición de estado no permitida. Consulte el ciclo de vida del caso.");
        }
        EstadoCaso anterior = caso.getEstado();
        caso.setEstado(EstadoCaso.ANULADO);
        caso.tocar();
        observacionCasoRepository.save(new ObservacionCaso(caso, usuario, request.justificacion().trim()));
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.ANULACION,
                "Caso anulado. Justificación: " + request.justificacion().trim(),
                ip,
                anterior,
                EstadoCaso.ANULADO);
        notificacionService.notificarCambioEstado(caso, anterior, EstadoCaso.ANULADO);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta cerrar(Long casoId, CerrarCasoRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_CERRAR);
        Caso caso = localizarVisible(casoId, usuario);
        if (caso.getEstado() != EstadoCaso.RESUELTO) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_APTO_CIERRE",
                    "El caso no cumple las condiciones necesarias para ser cerrado.");
        }
        EstadoCaso anterior = caso.getEstado();
        caso.setEstado(EstadoCaso.CERRADO);
        caso.tocar();
        observacionCasoRepository.save(new ObservacionCaso(caso, usuario, request.observacion().trim()));
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.CIERRE,
                "Cierre de " + caso.getCodigoSeguimiento() + ". " + request.observacion().trim(),
                ip,
                anterior,
                EstadoCaso.CERRADO);
        notificacionService.notificarCambioEstado(caso, anterior, EstadoCaso.CERRADO);
        return new AccionCasoRespuesta(
                "El caso fue cerrado y la operación quedó registrada en bitácora.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta modificar(Long casoId, ModificarCasoRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_GESTIONAR);
        Caso caso = localizarGestionable(casoId, usuario);
        exigirModificable(caso);
        Map<String, String> errores = validarModificacion(request, caso);
        if (!errores.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    errores);
        }
        List<String> cambios = aplicarCambios(caso, request, usuario);
        if (cambios.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    Map.of("motivo", "No se detectaron cambios respecto de la información vigente."));
        }
        caso.tocar();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.MODIFICACION,
                "Modificación de " + caso.getCodigoSeguimiento() + ": " + String.join("; ", cambios)
                        + ". Motivo: " + request.motivo().trim(),
                ip);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta prorrogar(Long casoId, ProrrogaRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_ASIGNAR);
        if (usuario.getRol() == Rol.AGENTE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
        Caso caso = localizarGestionable(casoId, usuario);
        exigirAbierto(caso);
        int dias = request.diasHabiles() == null ? 0 : request.diasHabiles();
        if (dias < 1 || dias > 15) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    Map.of("diasHabiles", "La prórroga debe ser de 1 a 15 días hábiles."));
        }
        LocalDate base = caso.getFechaLimiteRespuesta() == null ? LocalDate.now() : caso.getFechaLimiteRespuesta();
        caso.setFechaLimiteRespuesta(plazoService.fechaLimite(base, dias));
        caso.setFechaProrroga(LocalDate.now());
        caso.tocar();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.PRORROGA,
                "Prórroga de " + dias + " día(s) hábil(es) en " + caso.getCodigoSeguimiento()
                        + ". Justificación: " + request.justificacion().trim(),
                ip);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    @Transactional
    public AccionCasoRespuesta escalar(Long casoId, EscalarRequest request, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_ASIGNAR);
        if (usuario.getRol() == Rol.AGENTE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
        Caso caso = localizarGestionable(casoId, usuario);
        exigirAbierto(caso);
        caso.setPrioridad(Prioridad.ALTA);
        caso.setEscalado(true);
        caso.tocar();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.ESCALAMIENTO,
                "Escalamiento de " + caso.getCodigoSeguimiento() + ". Motivo: " + request.motivo().trim(),
                ip);
        return new AccionCasoRespuesta("Caso actualizado correctamente.", detalleDe(caso, usuario));
    }

    CasoDetalle detalleDe(Caso caso, Usuario usuario) {
        List<DocumentoResumen> documentos = documentoCasoRepository.findByCasoOrderBySubidoEnDesc(caso).stream()
                .map(DocumentoResumen::de)
                .toList();
        return CasoMapper.detalle(
                caso,
                usuario,
                documentos,
                observacionCasoRepository.findByCasoOrderByCreadoEnDesc(caso),
                bitacoraCasoRepository.findByCasoOrderByFechaHoraDesc(caso),
                plazoService);
    }

    Caso localizarVisible(Long casoId, Usuario usuario) {
        Caso caso = casoRepository.findById(casoId).orElseThrow(this::noDisponible);
        if (!puedeVer(usuario, caso)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene permisos para gestionar este caso.");
        }
        return caso;
    }

    Caso localizarGestionable(Long casoId, Usuario usuario) {
        Caso caso = localizarVisible(casoId, usuario);
        if (usuario.getRol() == Rol.AGENTE && !agentePuedeGestionar(usuario, caso)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene permisos para gestionar este caso.");
        }
        return caso;
    }

    private boolean puedeVer(Usuario usuario, Caso caso) {
        return switch (usuario.getRol()) {
            case ADMIN -> true;
            case SUPERVISOR -> mismaArea(usuario, caso);
            case AGENTE -> asignadoA(usuario, caso) || pendienteDeSuArea(usuario, caso);
            case CIUDADANO -> false;
        };
    }

    private boolean agentePuedeGestionar(Usuario usuario, Caso caso) {
        return asignadoA(usuario, caso) || pendienteDeSuArea(usuario, caso);
    }

    private boolean asignadoA(Usuario usuario, Caso caso) {
        return caso.getAgenteAsignado() != null
                && caso.getAgenteAsignado().getId().equals(usuario.getId());
    }

    private boolean pendienteDeSuArea(Usuario usuario, Caso caso) {
        return caso.getAgenteAsignado() == null && mismaArea(usuario, caso);
    }

    private boolean mismaArea(Usuario usuario, Caso caso) {
        return usuario.getAreaDependencia() != null
                && caso.getAreaDependencia() != null
                && usuario.getAreaDependencia().getId().equals(caso.getAreaDependencia().getId());
    }

    private Usuario agenteDelArea(Long agenteId, Caso caso) {
        Usuario agente = usuarioRepository.findById(agenteId).orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                "AGENTE_INVALIDO",
                "No hay agentes disponibles en esta área. Contacte al administrador."));
        if (agente.getRol() != Rol.AGENTE || !agente.isActivo()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AGENTE_INVALIDO",
                    "No hay agentes disponibles en esta área. Contacte al administrador.");
        }
        if (agente.getAreaDependencia() == null
                || caso.getAreaDependencia() == null
                || !agente.getAreaDependencia().getId().equals(caso.getAreaDependencia().getId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AGENTE_INVALIDO",
                    "El agente seleccionado no pertenece al área del caso.");
        }
        return agente;
    }

    void exigirNoFinalizado(Caso caso) {
        if (caso.getEstado().esFinal()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_DISPONIBLE",
                    "El caso seleccionado ya no se encuentra disponible para la operación.");
        }
    }

    void exigirModificable(Caso caso) {
        if (caso.getEstado().esFinal()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_MODIFICABLE",
                    "El estado actual del caso no permite la modificación solicitada.");
        }
    }

    void exigirAbierto(Caso caso) {
        if (caso.getEstado() != EstadoCaso.EN_REVISION && caso.getEstado() != EstadoCaso.EN_PROCESO) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CASO_NO_MODIFICABLE",
                    "El estado actual del caso no permite la modificación solicitada.");
        }
    }

    private Map<String, String> validarModificacion(ModificarCasoRequest request, Caso caso) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (request.descripcion() != null) {
            String texto = request.descripcion().trim();
            if (texto.length() < 50 || texto.length() > 2000) {
                errores.put("descripcion", "La descripción debe tener entre 50 y 2000 caracteres.");
            }
        }
        if (request.email() != null && !request.email().isBlank() && !EMAIL.matcher(request.email().trim()).matches()) {
            errores.put("email", "Ingrese un correo electrónico válido.");
        }
        if (request.prioridad() != null && !request.prioridad().isBlank()) {
            try {
                Prioridad.valueOf(request.prioridad().trim().toUpperCase());
            } catch (RuntimeException ex) {
                errores.put("prioridad", "Seleccione una prioridad válida.");
            }
        }
        if (request.areaDependencia() != null && !request.areaDependencia().isBlank()) {
            areaDependenciaRepository.findByCodigo(request.areaDependencia().trim().toUpperCase())
                    .filter(AreaDependencia::isActivo)
                    .orElseGet(() -> {
                        errores.put("areaDependencia", "Seleccione un área o dependencia.");
                        return null;
                    });
        }
        if (!caso.isEsAnonimo()
                && request.nombreCiudadano() != null
                && request.nombreCiudadano().trim().length() < 3) {
            errores.put("nombreCiudadano", "El nombre debe tener al menos 3 caracteres.");
        }
        return errores;
    }

    private List<String> aplicarCambios(Caso caso, ModificarCasoRequest request, Usuario usuario) {
        List<String> cambios = new ArrayList<>();
        if (!caso.isEsAnonimo() && request.nombreCiudadano() != null) {
            String valor = request.nombreCiudadano().trim();
            if (!valor.equals(nvl(caso.getNombreCiudadano()))) {
                caso.setNombreCiudadano(valor);
                cambios.add("nombre del ciudadano");
            }
        }
        if (request.email() != null) {
            String valor = request.email().trim();
            if (!valor.equals(nvl(caso.getEmailCiudadano()))) {
                caso.setEmailCiudadano(valor.isBlank() ? null : valor);
                cambios.add("correo");
            }
        }
        if (request.telefono() != null) {
            String valor = request.telefono().trim();
            if (!valor.equals(nvl(caso.getTelefono()))) {
                caso.setTelefono(valor.isBlank() ? null : valor);
                cambios.add("teléfono");
            }
        }
        if (request.descripcion() != null && !request.descripcion().trim().equals(caso.getDescripcion())) {
            caso.setDescripcion(request.descripcion().trim());
            cambios.add("descripción");
        }
        if (request.denunciado() != null) {
            String valor = request.denunciado().trim();
            if (!valor.equals(nvl(caso.getDenunciadoNombre()))) {
                caso.setDenunciadoNombre(valor.isBlank() ? null : valor);
                cambios.add("denunciado");
            }
        }
        if (request.prioridad() != null && !request.prioridad().isBlank()) {
            Prioridad prioridad = Prioridad.valueOf(request.prioridad().trim().toUpperCase());
            if (caso.getPrioridad() != prioridad) {
                caso.setPrioridad(prioridad);
                cambios.add("prioridad " + prioridad.name());
            }
        }
        if (request.areaDependencia() != null && !request.areaDependencia().isBlank()) {
            if (usuario.getRol() == Rol.AGENTE) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PERMISO_DENEGADO",
                        "No tiene autorización para realizar esta operación.");
            }
            AreaDependencia area = areaDependenciaRepository
                    .findByCodigo(request.areaDependencia().trim().toUpperCase())
                    .orElseThrow();
            if (!Objects.equals(area.getId(), caso.getAreaDependencia().getId())) {
                if (usuario.getRol() == Rol.SUPERVISOR
                        && (usuario.getAreaDependencia() == null
                                || !usuario.getAreaDependencia().getId().equals(area.getId()))) {
                    throw new ApiException(
                            HttpStatus.FORBIDDEN,
                            "PERMISO_DENEGADO",
                            "No tiene autorización para realizar esta operación.");
                }
                caso.setAreaDependencia(area);
                cambios.add("área " + area.getNombre());
            }
        }
        return cambios;
    }

    private String nvl(String valor) {
        return valor == null ? "" : valor;
    }

    private EstadoCaso parsearEstado(String valor) {
        try {
            return EstadoCaso.valueOf(valor.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSICION_INVALIDA",
                    "Transición de estado no permitida. Consulte el ciclo de vida del caso.");
        }
    }

    private ApiException noDisponible() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "CASO_NO_DISPONIBLE",
                "El caso seleccionado ya no se encuentra disponible para la operación.");
    }
}
