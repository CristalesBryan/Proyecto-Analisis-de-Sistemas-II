package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.AuthDtos.ConfirmarRegistroRequest;
import gt.municipalidad.qrds.dto.AuthDtos.LoginRequest;
import gt.municipalidad.qrds.dto.AuthDtos.LoginResponse;
import gt.municipalidad.qrds.dto.AuthDtos.MensajeResponse;
import gt.municipalidad.qrds.dto.AuthDtos.RegistroCiudadanoInicioResponse;
import gt.municipalidad.qrds.dto.AuthDtos.RegistroCiudadanoRequest;
import gt.municipalidad.qrds.dto.AuthDtos.SesionResponse;
import gt.municipalidad.qrds.service.AuthService;
import gt.municipalidad.qrds.service.CiudadanoRegistroService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CiudadanoRegistroService ciudadanoRegistroService;

    public AuthController(AuthService authService, CiudadanoRegistroService ciudadanoRegistroService) {
        this.authService = authService;
        this.ciudadanoRegistroService = ciudadanoRegistroService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, HttpRequests.ipCliente(http), HttpRequests.userAgent(http));
    }

    @PostMapping("/ciudadano/verificar-inicio")
    public RegistroCiudadanoInicioResponse iniciarRegistro(
            @Valid @RequestBody RegistroCiudadanoRequest request,
            HttpServletRequest http) {
        return ciudadanoRegistroService.iniciar(
                request, HttpRequests.ipCliente(http), HttpRequests.userAgent(http));
    }

    @PostMapping("/ciudadano/confirmar")
    public MensajeResponse confirmarRegistro(
            @Valid @RequestBody ConfirmarRegistroRequest request,
            HttpServletRequest http) {
        return ciudadanoRegistroService.confirmar(
                request, HttpRequests.ipCliente(http), HttpRequests.userAgent(http));
    }

    @PostMapping("/logout")
    public MensajeResponse logout(Authentication authentication, HttpServletRequest http) {
        authService.logout(authentication, HttpRequests.ipCliente(http), HttpRequests.userAgent(http));
        return new MensajeResponse("Sesión cerrada correctamente.");
    }

    @GetMapping("/me")
    public SesionResponse me(Authentication authentication) {
        return new SesionResponse(authService.sesionActual(authentication));
    }
}
