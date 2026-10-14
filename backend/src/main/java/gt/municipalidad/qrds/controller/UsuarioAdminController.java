package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.UsuarioDtos.ActualizarUsuarioRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.AreaOpcion;
import gt.municipalidad.qrds.dto.UsuarioDtos.CrearUsuarioRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.PaginaUsuarios;
import gt.municipalidad.qrds.dto.UsuarioDtos.ResetPasswordRequest;
import gt.municipalidad.qrds.dto.UsuarioDtos.UsuarioAccionResponse;
import gt.municipalidad.qrds.dto.UsuarioDtos.UsuarioResumen;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.UsuarioAdminService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarios;

    public UsuarioAdminController(UsuarioAdminService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/catalogos/areas")
    public List<AreaOpcion> areas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return usuarios.areasDisponibles(principal.getUsuario());
    }

    @GetMapping
    public PaginaUsuarios listar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return usuarios.listar(principal.getUsuario(), q, rol, area, activo, page, size);
    }

    @GetMapping("/{id}")
    public UsuarioResumen obtener(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id) {
        return usuarios.obtener(principal.getUsuario(), id);
    }

    @PostMapping
    public UsuarioAccionResponse crear(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CrearUsuarioRequest request,
            HttpServletRequest http) {
        return usuarios.crear(principal.getUsuario(), request, HttpRequests.ipCliente(http));
    }

    @PutMapping("/{id}")
    public UsuarioAccionResponse actualizar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest request,
            HttpServletRequest http) {
        return usuarios.actualizar(principal.getUsuario(), id, request, HttpRequests.ipCliente(http));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioAccionResponse restablecer(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) ResetPasswordRequest request,
            HttpServletRequest http) {
        return usuarios.restablecer(principal.getUsuario(), id, request, HttpRequests.ipCliente(http));
    }
}
