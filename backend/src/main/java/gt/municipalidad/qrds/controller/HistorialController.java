package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.HistorialDtos.PaginaHistorial;
import gt.municipalidad.qrds.dto.HistorialDtos.VistaCiudadano;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.HistorialCasoService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistorialController {

    private final HistorialCasoService historialCasoService;

    public HistorialController(HistorialCasoService historialCasoService) {
        this.historialCasoService = historialCasoService;
    }

    @GetMapping("/api/casos/{casoId}/historial")
    @PreAuthorize("hasAuthority('PERM_CASOS_VER')")
    public PaginaHistorial consultar(
            @PathVariable Long casoId,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {
        return historialCasoService.consultar(casoId, principal.getUsuario(), categoria, q, page, size);
    }

    @GetMapping("/api/casos/{casoId}/historial/vista-ciudadano")
    @PreAuthorize("hasAuthority('PERM_CASOS_VER')")
    public VistaCiudadano vistaCiudadano(
            @PathVariable Long casoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return historialCasoService.vistaCiudadano(casoId, principal.getUsuario());
    }

    @GetMapping("/api/casos/{casoId}/historial/export.pdf")
    @PreAuthorize("hasAuthority('PERM_CASOS_VER')")
    public ResponseEntity<byte[]> exportar(
            @PathVariable Long casoId,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String q,
            HttpServletRequest request) {
        return historialCasoService.exportar(
                casoId, principal.getUsuario(), categoria, q, HttpRequests.ipCliente(request));
    }
}
