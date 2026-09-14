package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.AuthDtos.LoginRequest;
import gt.municipalidad.qrds.dto.AuthDtos.LoginResponse;
import gt.municipalidad.qrds.dto.AuthDtos.UsuarioAutenticado;
import gt.municipalidad.qrds.entity.TipoEventoAcceso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import gt.municipalidad.qrds.security.JwtService;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String MSG_CREDENCIALES =
            "Usuario o contraseña incorrectos. Verifique sus datos e intente nuevamente.";
    private static final String MSG_INACTIVO =
            "Su cuenta está inactiva o bloqueada. Contacte al administrador del sistema.";
    private static final String MSG_BLOQUEADA =
            "Cuenta bloqueada temporalmente por múltiples intentos fallidos. Intente en 15 minutos o contacte al administrador.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BitacoraAccesoService bitacoraAccesoService;
    private final int maxIntentos;
    private final int bloqueoMinutos;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            BitacoraAccesoService bitacoraAccesoService,
            @Value("${qrds.login.max-intentos}") int maxIntentos,
            @Value("${qrds.login.bloqueo-minutos}") int bloqueoMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.bitacoraAccesoService = bitacoraAccesoService;
        this.maxIntentos = maxIntentos;
        this.bloqueoMinutos = bloqueoMinutos;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String email = request.email() == null ? "" : request.email().trim();
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        if (usuario == null) {
            bitacoraAccesoService.registrar(
                    null, TipoEventoAcceso.LOGIN_FALLIDO, ip, userAgent, "FALLIDO",
                    "Intento de acceso con usuario inexistente.");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MSG_CREDENCIALES);
        }

        if (usuario.estaBloqueado()) {
            bitacoraAccesoService.registrar(
                    usuario, TipoEventoAcceso.BLOQUEADO, ip, userAgent, "BLOQUEADO",
                    "Intento de acceso con cuenta bloqueada temporalmente.");
            throw new ApiException(HttpStatus.FORBIDDEN, "CUENTA_BLOQUEADA", MSG_BLOQUEADA);
        }

        if (usuario.getBloqueadoHasta() != null) {
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosFallidos(0);
        }

        if (!usuario.isActivo()) {
            bitacoraAccesoService.registrar(
                    usuario, TipoEventoAcceso.LOGIN_FALLIDO, ip, userAgent, "INACTIVO",
                    "Intento de acceso con usuario inactivo.");
            throw new ApiException(HttpStatus.FORBIDDEN, "USUARIO_INACTIVO", MSG_INACTIVO);
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            usuario.registrarIntentoFallido(maxIntentos, bloqueoMinutos);
            if (usuario.estaBloqueado()) {
                bitacoraAccesoService.registrar(
                        usuario, TipoEventoAcceso.BLOQUEADO, ip, userAgent, "BLOQUEADO",
                        "Cuenta bloqueada por superar el máximo de intentos fallidos.");
                throw new ApiException(HttpStatus.FORBIDDEN, "CUENTA_BLOQUEADA", MSG_BLOQUEADA);
            }
            bitacoraAccesoService.registrar(
                    usuario, TipoEventoAcceso.LOGIN_FALLIDO, ip, userAgent, "FALLIDO",
                    "Credenciales inválidas.");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MSG_CREDENCIALES);
        }

        usuario.registrarAccesoExitoso();
        String token = jwtService.generar(usuario);
        bitacoraAccesoService.registrar(
                usuario, TipoEventoAcceso.LOGIN, ip, userAgent, "OK",
                "Inicio de sesión exitoso. Rol " + usuario.getRol().name() + ".");
        return new LoginResponse(token, UsuarioAutenticado.de(usuario));
    }

    @Transactional
    public void logout(Authentication authentication, String ip, String userAgent) {
        Usuario usuario = usuarioDe(authentication);
        usuario.invalidarSesiones();
        bitacoraAccesoService.registrar(
                usuario, TipoEventoAcceso.LOGOUT, ip, userAgent, "OK",
                "Cierre de sesión.");
    }

    @Transactional(readOnly = true)
    public UsuarioAutenticado sesionActual(Authentication authentication) {
        return UsuarioAutenticado.de(usuarioDe(authentication));
    }

    private Usuario usuarioDe(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "TOKEN_INVALIDO",
                    "Su sesión ha expirado. Por favor inicie sesión nuevamente.");
        }
        Long id = principal.getUsuario().getId();
        return usuarioRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "TOKEN_INVALIDO",
                "Su sesión ha expirado. Por favor inicie sesión nuevamente."));
    }
}
