package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.UsuarioDtos.CambiarPasswordRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.CambiarPasswordResponse;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.UsuarioAdminService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios/me")
public class PerfilController {

    private final UsuarioAdminService usuarios;

    public PerfilController(UsuarioAdminService usuarios) {
        this.usuarios = usuarios;
    }

    @PostMapping("/cambiar-password")
    @PreAuthorize("isAuthenticated()")
    public CambiarPasswordResponse cambiarPassword(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CambiarPasswordRequest request,
            HttpServletRequest http) {
        return usuarios.cambiarPropia(principal.getUsuario(), request, HttpRequests.ipCliente(http));
    }
}
