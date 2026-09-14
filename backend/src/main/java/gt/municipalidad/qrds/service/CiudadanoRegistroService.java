package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.AuthDtos.ConfirmarRegistroRequest;
import gt.municipalidad.qrds.dto.AuthDtos.MensajeResponse;
import gt.municipalidad.qrds.dto.AuthDtos.RegistroCiudadanoInicioResponse;
import gt.municipalidad.qrds.dto.AuthDtos.RegistroCiudadanoRequest;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.TipoEventoAcceso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CiudadanoRegistroService {

    private static final Logger log = LoggerFactory.getLogger(CiudadanoRegistroService.class);
    private static final long TTL_SEGUNDOS = 10 * 60;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern DPI = Pattern.compile("^\\d{13}$");
    private static final Pattern PASSWORD_FUERTE = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,}$");

    private final UsuarioRepository usuarioRepository;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder;
    private final BitacoraAccesoService bitacoraAccesoService;
    private final String codigoPrueba;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();

    public CiudadanoRegistroService(
            UsuarioRepository usuarioRepository,
            CaptchaService captchaService,
            PasswordEncoder passwordEncoder,
            BitacoraAccesoService bitacoraAccesoService,
            @Value("${qrds.registro.codigo-prueba:}") String codigoPrueba) {
        this.usuarioRepository = usuarioRepository;
        this.captchaService = captchaService;
        this.passwordEncoder = passwordEncoder;
        this.bitacoraAccesoService = bitacoraAccesoService;
        this.codigoPrueba = codigoPrueba == null ? "" : codigoPrueba.trim();
    }

    public RegistroCiudadanoInicioResponse iniciar(RegistroCiudadanoRequest request, String ip, String userAgent) {
        captchaService.verificar(request.captchaId(), request.captchaRespuesta());
        Map<String, String> errores = validar(request);
        if (!errores.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION",
                    "Datos inválidos. Verifique el formulario.",
                    errores);
        }

        String email = texto(request.email()).toLowerCase(Locale.ROOT);
        String dpi = digitos(request.dpi());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_DUPLICADO",
                    "Ya existe una cuenta con este correo electrónico.");
        }
        if (usuarioRepository.existsByDpi(dpi)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DPI_DUPLICADO",
                    "Ya existe una cuenta con este DPI.");
        }

        limpiarExpirados();
        String registroId = UUID.randomUUID().toString();
        String codigo = codigoPrueba.isBlank()
                ? String.format("%06d", random.nextInt(1_000_000))
                : codigoPrueba;
        pendientes.put(
                registroId,
                new Pendiente(
                        texto(request.nombre()),
                        email,
                        digitos(request.telefono()),
                        dpi,
                        passwordEncoder.encode(request.password()),
                        codigo,
                        Instant.now().plusSeconds(TTL_SEGUNDOS)));

        log.info("Código de verificación de registro para {}: {}", email, codigo);
        registrarBitacora(
                null,
                TipoEventoAcceso.REGISTRO_CIUDADANO,
                ip,
                userAgent,
                "PENDIENTE",
                "Inicio de registro ciudadano. Pendiente de verificación de correo.");
        return new RegistroCiudadanoInicioResponse(
                registroId,
                "Enviamos un código de verificación a su correo. Ingréselo para activar la cuenta.");
    }

    @Transactional
    public MensajeResponse confirmar(ConfirmarRegistroRequest request, String ip, String userAgent) {
        limpiarExpirados();
        Pendiente pendiente = request.registroId() == null
                ? null
                : pendientes.remove(request.registroId().trim());
        if (pendiente == null || Instant.now().isAfter(pendiente.expira())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CODIGO_INVALIDO",
                    "El código expiró o es incorrecto. Inicie el registro nuevamente.");
        }
        if (!pendiente.codigo().equals(texto(request.codigo()))) {
            pendientes.put(request.registroId().trim(), pendiente);
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CODIGO_INVALIDO",
                    "El código de verificación es incorrecto.");
        }
        if (usuarioRepository.existsByEmailIgnoreCase(pendiente.email())
                || usuarioRepository.existsByDpi(pendiente.dpi())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CUENTA_EXISTENTE",
                    "Ya existe una cuenta con estos datos.");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(pendiente.nombre());
        usuario.setEmail(pendiente.email());
        usuario.setTelefono(pendiente.telefono().isEmpty() ? null : pendiente.telefono());
        usuario.setDpi(pendiente.dpi());
        usuario.setPasswordHash(pendiente.passwordHash());
        usuario.setRol(Rol.CIUDADANO);
        usuario.setActivo(true);
        usuario.setAceptaPrivacidad(true);
        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);

        registrarBitacora(
                null,
                TipoEventoAcceso.REGISTRO_CIUDADANO,
                ip,
                userAgent,
                "OK",
                "Cuenta de ciudadano creada y verificada. Correo " + pendiente.email() + ".");
        return new MensajeResponse("Cuenta creada. Ya puede iniciar sesión con su correo y contraseña.");
    }

    private void registrarBitacora(
            Usuario usuario,
            TipoEventoAcceso tipoEvento,
            String ip,
            String userAgent,
            String resultado,
            String detalle) {
        try {
            bitacoraAccesoService.registrar(usuario, tipoEvento, ip, userAgent, resultado, detalle);
        } catch (RuntimeException ex) {
            log.warn("No se pudo guardar la bitácora de registro ciudadano: {}", ex.getMessage());
        }
    }

    private Map<String, String> validar(RegistroCiudadanoRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (!request.aceptaPrivacidad()) {
            errores.put("aceptaPrivacidad", "Debe aceptar el aviso de privacidad para crear su cuenta.");
        }
        String nombre = texto(request.nombre());
        if (nombre.length() < 2 || nombre.length() > 150) {
            errores.put("nombre", "El nombre debe tener entre 2 y 150 caracteres.");
        }
        String email = texto(request.email());
        if (!EMAIL.matcher(email).matches()) {
            errores.put("email", "Ingrese un correo electrónico válido.");
        }
        String telefono = digitos(request.telefono());
        if (!telefono.isEmpty() && (telefono.length() < 8 || telefono.length() > 15)) {
            errores.put("telefono", "El teléfono debe tener entre 8 y 15 dígitos.");
        }
        String dpi = digitos(request.dpi());
        if (!DPI.matcher(dpi).matches()) {
            errores.put("dpi", "Ingrese un DPI o CUI de 13 dígitos.");
        }
        String password = request.password() == null ? "" : request.password();
        if (!PASSWORD_FUERTE.matcher(password).matches()) {
            errores.put(
                    "password",
                    "La contraseña debe tener al menos 10 caracteres, mayúscula, minúscula, número y un símbolo.");
        }
        if (!password.equals(request.confirmarPassword() == null ? "" : request.confirmarPassword())) {
            errores.put("confirmarPassword", "Las contraseñas no coinciden.");
        }
        return errores;
    }

    private void limpiarExpirados() {
        Instant ahora = Instant.now();
        pendientes.entrySet().removeIf(entry -> ahora.isAfter(entry.getValue().expira()));
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String digitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private record Pendiente(
            String nombre,
            String email,
            String telefono,
            String dpi,
            String passwordHash,
            String codigo,
            Instant expira) {
    }
}
