package gt.municipalidad.qrds.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.dto.AuthDtos;
import gt.municipalidad.qrds.dto.UsuarioDtos.ActualizarUsuarioRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.AreaOpcion;
import gt.municipalidad.qrds.dto.UsuarioDtos.CambiarPasswordRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.CambiarPasswordResponse;
import gt.municipalidad.qrds.dto.UsuarioDtos.CrearUsuarioRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.PaginaUsuarios;
import gt.municipalidad.qrds.dto.UsuarioDtos.ResetPasswordRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.UsuarioAccionResponse;
import gt.municipalidad.qrds.dto.UsuarioDtos.UsuarioResumen;
import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import gt.municipalidad.qrds.security.JwtService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioAdminService {

    static final String DOMINIO = "USUARIOS";
    static final String ENTIDAD = "USUARIO";
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioRepository usuarios;
    private final AreaDependenciaRepository areas;
    private final PasswordEncoder passwordEncoder;
    private final BitacoraSistemaService bitacora;
    private final NotificacionService notificaciones;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public UsuarioAdminService(
            UsuarioRepository usuarios,
            AreaDependenciaRepository areas,
            PasswordEncoder passwordEncoder,
            BitacoraSistemaService bitacora,
            NotificacionService notificaciones,
            JwtService jwtService,
            ObjectMapper objectMapper) {
        this.usuarios = usuarios;
        this.areas = areas;
        this.passwordEncoder = passwordEncoder;
        this.bitacora = bitacora;
        this.notificaciones = notificaciones;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AreaOpcion> areasDisponibles(Usuario actorSesion) {
        Usuario actor = cargar(actorSesion.getId());
        if (actor.getRol() == Rol.SUPERVISOR) {
            AreaDependencia area = areaDelActor(actor);
            return List.of(AreaOpcion.de(area));
        }
        return areas.findByActivoTrueOrderByNombreAsc().stream().map(AreaOpcion::de).toList();
    }

    @Transactional(readOnly = true)
    public PaginaUsuarios listar(
            Usuario actorSesion,
            String consulta,
            String rol,
            String area,
            Boolean activo,
            int pagina,
            int tamano) {
        Usuario actor = cargar(actorSesion.getId());
        Rol rolFiltro = rolInternoOpcional(rol);
        int size = tamano < 1 ? 20 : Math.min(tamano, 50);
        int page = Math.max(pagina, 0);
        Specification<Usuario> especificacion = (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.notEqual(root.get("rol"), Rol.CIUDADANO));
            if (actor.getRol() == Rol.SUPERVISOR) {
                predicados.add(cb.equal(root.get("rol"), Rol.AGENTE));
                predicados.add(cb.equal(root.get("areaDependencia"), areaDelActor(actor)));
            } else {
                if (rolFiltro != null) {
                    predicados.add(cb.equal(root.get("rol"), rolFiltro));
                }
                if (area != null && !area.isBlank()) {
                    predicados.add(cb.equal(root.get("areaDependencia").get("codigo"), area.trim()));
                }
            }
            if (activo != null) {
                predicados.add(cb.equal(root.get("activo"), activo));
            }
            if (consulta != null && !consulta.isBlank()) {
                String like = "%" + consulta.trim().toLowerCase(Locale.ROOT) + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
        Page<Usuario> resultado = usuarios.findAll(
                especificacion,
                PageRequest.of(page, size, Sort.by("nombre").ascending()));
        return new PaginaUsuarios(
                resultado.getContent().stream().map(UsuarioResumen::de).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UsuarioResumen obtener(Usuario actorSesion, Long id) {
        Usuario actor = cargar(actorSesion.getId());
        return UsuarioResumen.de(objetivoVisible(actor, id));
    }

    @Transactional
    public UsuarioAccionResponse crear(Usuario actorSesion, CrearUsuarioRequest request, String ip) {
        Usuario actor = cargar(actorSesion.getId());
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = texto(request.nombre());
        String email = texto(request.email());
        validarNombre(nombre, errores);
        validarCorreo(email, errores);
        Rol rol = rolInterno(request.rol(), errores);
        boolean generar = Boolean.TRUE.equals(request.generarPassword());
        String password = request.password() == null ? "" : request.password();
        if (generar && password.isBlank()) {
            password = PasswordPolicy.generar();
        } else if (!PasswordPolicy.cumple(password)) {
            errores.put("password", PasswordPolicy.MENSAJE);
        }
        AreaDependencia area = resolverArea(actor, rol, request.areaId(), errores);
        if (actor.getRol() == Rol.SUPERVISOR && rol != null && rol != Rol.AGENTE) {
            denegar(actor, null, ip, "El supervisor intentó crear un usuario con rol " + rol + ".");
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No puede crear administradores ni supervisores.");
        }
        if (!errores.isEmpty()) {
            throw validacion(errores);
        }
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "CORREO_DUPLICADO", "El correo ya está registrado.");
        }
        Usuario nuevo = new Usuario();
        nuevo.setNombre(nombre);
        nuevo.setEmail(email);
        nuevo.setRol(rol);
        nuevo.setAreaDependencia(area);
        nuevo.setActivo(request.activo() == null || request.activo());
        nuevo.setPasswordHash(passwordEncoder.encode(password));
        nuevo.setForzarCambioPassword(true);
        nuevo.setEmailVerificado(true);
        nuevo.tocar();
        usuarios.save(nuevo);
        bitacora.registrarEnCurso(
                DOMINIO,
                ENTIDAD,
                "USUARIO_CREADO",
                actor,
                nuevo.getId(),
                "Creación de usuario " + nuevo.getEmail() + " con rol " + nuevo.getRol().name() + ".",
                ip,
                meta(Map.of(
                        "usuarioAfectado", nuevo.getId(),
                        "rol", nuevo.getRol().name(),
                        "area", area == null ? "" : area.getCodigo())));
        notificaciones.notificarPrimerAcceso(nuevo);
        String temporal = generar && (request.password() == null || request.password().isBlank()) ? password : null;
        return new UsuarioAccionResponse("El usuario quedó registrado.", UsuarioResumen.de(nuevo), temporal);
    }

    @Transactional
    public UsuarioAccionResponse actualizar(Usuario actorSesion, Long id, ActualizarUsuarioRequest request, String ip) {
        Usuario actor = cargar(actorSesion.getId());
        Usuario objetivo = objetivoVisible(actor, id);
        Map<String, String> errores = new LinkedHashMap<>();
        String nombre = texto(request.nombre());
        String email = texto(request.email());
        validarNombre(nombre, errores);
        validarCorreo(email, errores);
        Rol rol = rolInterno(request.rol(), errores);
        if (actor.getRol() == Rol.SUPERVISOR && rol != null && rol != Rol.AGENTE) {
            denegar(actor, objetivo.getId(), ip, "El supervisor intentó cambiar el rol de un agente.");
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No puede cambiar roles.");
        }
        AreaDependencia area = resolverArea(actor, rol, request.areaId(), errores);
        if (!errores.isEmpty()) {
            throw validacion(errores);
        }
        if (actor.getId().equals(objetivo.getId()) && rol == Rol.ADMIN && objetivo.getRol() != Rol.ADMIN) {
            denegar(actor, objetivo.getId(), ip, "Intento de elevar el propio rol a administrador.");
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISO_DENEGADO", "No puede elevar su propio rol.");
        }
        boolean activo = Boolean.TRUE.equals(request.activo());
        exigirAdministradorActivo(actor, objetivo, rol, activo, ip);
        if (!email.equalsIgnoreCase(objetivo.getEmail()) && usuarios.existsByEmailIgnoreCaseAndIdNot(email, objetivo.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CORREO_DUPLICADO", "El correo ya está registrado.");
        }
        Rol anterior = objetivo.getRol();
        boolean estabaActivo = objetivo.isActivo();
        String nombreAnterior = objetivo.getNombre();
        String emailAnterior = objetivo.getEmail();
        String areaAnterior = objetivo.getAreaDependencia() == null ? "" : objetivo.getAreaDependencia().getCodigo();
        objetivo.setNombre(nombre);
        objetivo.setEmail(email);
        objetivo.setRol(rol);
        objetivo.setAreaDependencia(area);
        objetivo.setActivo(activo);
        if (activo && !estabaActivo) {
            objetivo.limpiarBloqueo();
        }
        if (anterior != rol) {
            objetivo.invalidarSesiones();
        }
        if (!activo) {
            objetivo.invalidarSesiones();
        }
        objetivo.tocar();
        usuarios.save(objetivo);
        if (anterior != rol) {
            registrar(actor, objetivo, "ROL_CAMBIADO", ip,
                    "Cambio de rol de " + anterior.name() + " a " + rol.name() + ".",
                    Map.of("rolAnterior", anterior.name(), "rolNuevo", rol.name(), "usuarioAfectado", objetivo.getId()));
        }
        if (estabaActivo && !activo) {
            registrar(actor, objetivo, "USUARIO_DESACTIVADO", ip,
                    "Desactivación de " + objetivo.getEmail() + ".",
                    Map.of("usuarioAfectado", objetivo.getId(), "activo", false));
        } else if (!estabaActivo && activo) {
            registrar(actor, objetivo, "USUARIO_REACTIVADO", ip,
                    "Reactivación de " + objetivo.getEmail() + ".",
                    Map.of("usuarioAfectado", objetivo.getId(), "activo", true));
        }
        String areaNueva = area == null ? "" : area.getCodigo();
        if (!nombre.equals(nombreAnterior) || !email.equalsIgnoreCase(emailAnterior) || !areaAnterior.equals(areaNueva)) {
            registrar(actor, objetivo, "USUARIO_EDITADO", ip,
                    "Edición de " + objetivo.getEmail() + ".",
                    Map.of(
                            "usuarioAfectado", objetivo.getId(),
                            "areaAnterior", areaAnterior,
                            "areaNueva", areaNueva));
        }
        return new UsuarioAccionResponse("El usuario quedó actualizado.", UsuarioResumen.de(objetivo), null);
    }

    @Transactional
    public UsuarioAccionResponse restablecer(Usuario actorSesion, Long id, ResetPasswordRequest request, String ip) {
        Usuario actor = cargar(actorSesion.getId());
        if (actor.getRol() != Rol.ADMIN) {
            denegar(actor, id, ip, "Intento de restablecer la contraseña de otro usuario.");
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No puede restablecer contraseñas de otros usuarios.");
        }
        Usuario objetivo = objetivoVisible(actor, id);
        String nueva = request == null || request.nuevaPassword() == null ? "" : request.nuevaPassword();
        boolean generada = nueva.isBlank();
        if (generada) {
            nueva = PasswordPolicy.generar();
        } else if (!PasswordPolicy.cumple(nueva)) {
            throw validacion(Map.of("nuevaPassword", PasswordPolicy.MENSAJE));
        }
        objetivo.setPasswordHash(passwordEncoder.encode(nueva));
        objetivo.setForzarCambioPassword(true);
        objetivo.invalidarSesiones();
        objetivo.tocar();
        usuarios.save(objetivo);
        registrar(actor, objetivo, "PASSWORD_RESET", ip,
                "Restablecimiento de contraseña de " + objetivo.getEmail() + ".",
                Map.of("usuarioAfectado", objetivo.getId()));
        notificaciones.notificarRestablecimiento(objetivo);
        return new UsuarioAccionResponse(
                "La contraseña fue restablecida. El usuario deberá cambiarla en el siguiente ingreso.",
                UsuarioResumen.de(objetivo),
                generada ? nueva : null);
    }

    @Transactional
    public CambiarPasswordResponse cambiarPropia(Usuario actorSesion, CambiarPasswordRequest request, String ip) {
        Usuario actor = cargar(actorSesion.getId());
        Map<String, String> errores = new LinkedHashMap<>();
        if (!passwordEncoder.matches(request.actual(), actor.getPasswordHash())) {
            errores.put("actual", "La contraseña actual no es correcta.");
        }
        if (!PasswordPolicy.cumple(request.nueva())) {
            errores.put("nueva", PasswordPolicy.MENSAJE);
        }
        if (request.nueva() != null && !request.nueva().equals(request.confirmacion())) {
            errores.put("confirmacion", "La confirmación no coincide con la nueva contraseña.");
        }
        if (!errores.isEmpty()) {
            throw validacion(errores);
        }
        actor.setPasswordHash(passwordEncoder.encode(request.nueva()));
        actor.setForzarCambioPassword(false);
        actor.invalidarSesiones();
        actor.tocar();
        usuarios.save(actor);
        registrar(actor, actor, "PASSWORD_CAMBIO_PROPIO", ip,
                "Cambio de contraseña propia de " + actor.getEmail() + ".",
                Map.of("usuarioAfectado", actor.getId()));
        return new CambiarPasswordResponse(
                "La contraseña fue actualizada.",
                jwtService.generar(actor),
                AuthDtos.UsuarioAutenticado.de(actor));
    }

    private Usuario objetivoVisible(Usuario actor, Long id) {
        Usuario objetivo = usuarios.findById(id).orElseThrow(this::noEncontrado);
        if (objetivo.getRol() == Rol.CIUDADANO) {
            throw noEncontrado();
        }
        if (actor.getRol() == Rol.SUPERVISOR) {
            AreaDependencia area = areaDelActor(actor);
            boolean mismoAlcance = objetivo.getRol() == Rol.AGENTE
                    && objetivo.getAreaDependencia() != null
                    && area.getId().equals(objetivo.getAreaDependencia().getId());
            if (!mismoAlcance) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PERMISO_DENEGADO",
                        "No puede administrar usuarios fuera de su área.");
            }
        }
        return objetivo;
    }

    private AreaDependencia resolverArea(Usuario actor, Rol rol, Long areaId, Map<String, String> errores) {
        if (rol == null) {
            return null;
        }
        if (actor.getRol() == Rol.SUPERVISOR) {
            return areaDelActor(actor);
        }
        if (rol == Rol.ADMIN) {
            if (areaId == null) {
                return null;
            }
            return areaActiva(areaId, errores);
        }
        if (areaId == null) {
            errores.put("areaId", "El área es obligatoria para este rol.");
            return null;
        }
        return areaActiva(areaId, errores);
    }

    private AreaDependencia areaActiva(Long areaId, Map<String, String> errores) {
        AreaDependencia area = areas.findById(areaId).orElse(null);
        if (area == null || !area.isActivo()) {
            errores.put("areaId", "El área seleccionada no está disponible.");
            return null;
        }
        return area;
    }

    private AreaDependencia areaDelActor(Usuario actor) {
        if (actor.getAreaDependencia() == null) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "Su usuario no tiene un área asignada.");
        }
        return actor.getAreaDependencia();
    }

    private void exigirAdministradorActivo(Usuario actor, Usuario objetivo, Rol nuevoRol, boolean activo, String ip) {
        boolean dejaDeSerAdmin = objetivo.getRol() == Rol.ADMIN
                && objetivo.isActivo()
                && (nuevoRol != Rol.ADMIN || !activo);
        if (!dejaDeSerAdmin) {
            return;
        }
        if (usuarios.countByRolAndActivoTrueAndIdNot(Rol.ADMIN, objetivo.getId()) > 0) {
            return;
        }
        denegar(actor, objetivo.getId(), ip, "Intento de dejar el sistema sin un administrador activo.");
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ULTIMO_ADMIN",
                "Debe existir al menos un administrador activo.");
    }

    private void registrar(Usuario actor, Usuario objetivo, String evento, String ip, String descripcion, Map<String, ?> datos) {
        bitacora.registrarEnCurso(DOMINIO, ENTIDAD, evento, actor, objetivo.getId(), descripcion, ip, meta(datos));
    }

    private void denegar(Usuario actor, Long entidadId, String ip, String descripcion) {
        bitacora.registrarNueva(
                DOMINIO,
                ENTIDAD,
                "INTENTO_DENEGADO",
                actor,
                entidadId,
                descripcion,
                ip,
                meta(Map.of("resultado", "DENEGADO")));
    }

    private Usuario cargar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "TOKEN_INVALIDO",
                "Su sesión ha expirado. Por favor inicie sesión nuevamente."));
    }

    private Rol rolInternoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        Rol rol = rolDe(valor);
        if (rol == null || rol == Rol.CIUDADANO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROL_INVALIDO", "El rol no está permitido.");
        }
        return rol;
    }

    private Rol rolInterno(String valor, Map<String, String> errores) {
        Rol rol = rolDe(valor);
        if (rol == null || rol == Rol.CIUDADANO) {
            errores.put("rol", "El rol no está permitido.");
            return null;
        }
        return rol;
    }

    private Rol rolDe(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Rol.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void validarNombre(String nombre, Map<String, String> errores) {
        if (nombre.length() < 2 || nombre.length() > 100) {
            errores.put("nombre", "El nombre debe tener entre 2 y 100 caracteres.");
        }
    }

    private void validarCorreo(String email, Map<String, String> errores) {
        if (!CORREO.matcher(email).matches()) {
            errores.put("email", "Ingrese un correo electrónico válido.");
        }
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private ApiException validacion(Map<String, String> errores) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDACION",
                "Datos inválidos. Verifique el formulario.",
                errores);
    }

    private ApiException noEncontrado() {
        return new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El usuario no existe.");
    }

    private String meta(Map<String, ?> datos) {
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
