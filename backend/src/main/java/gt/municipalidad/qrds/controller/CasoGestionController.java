package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.CasoDtos.AccionCasoRespuesta;
import gt.municipalidad.qrds.dto.CasoDtos.AgenteOpcion;
import gt.municipalidad.qrds.dto.CasoDtos.AnularRequest;
import gt.municipalidad.qrds.dto.CasoDtos.AsignarRequest;
import gt.municipalidad.qrds.dto.CasoDtos.CambioEstadoRequest;
import gt.municipalidad.qrds.dto.CasoDtos.CerrarCasoRequest;
import gt.municipalidad.qrds.dto.CasoDtos.CasoDetalle;
import gt.municipalidad.qrds.dto.CasoDtos.EscalarRequest;
import gt.municipalidad.qrds.dto.CasoDtos.ModificarCasoRequest;
import gt.municipalidad.qrds.dto.CasoDtos.ObservacionRequest;
import gt.municipalidad.qrds.dto.CasoDtos.PaginaCasos;
import gt.municipalidad.qrds.dto.CasoDtos.ProrrogaRequest;
import gt.municipalidad.qrds.dto.CasoDtos.ReasignarRequest;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.CasoGestionService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CasoGestionController {

    private final CasoGestionService casoGestionService;

    public CasoGestionController(CasoGestionService casoGestionService) {
        this.casoGestionService = casoGestionService;
    }

    @GetMapping("/api/casos")
    @PreAuthorize("hasAuthority('PERM_CASOS_VER')")
    public PaginaCasos listar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean sinAsignar,
            @RequestParam(required = false, defaultValue = "fechaRegistro") String orden,
            @RequestParam(required = false, defaultValue = "desc") String direccion,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return casoGestionService.listar(
                principal.getUsuario(),
                estado,
                tipo,
                area,
                codigo,
                desde,
                hasta,
                sinAsignar,
                orden,
                direccion,
                page,
                size);
    }

    @GetMapping("/api/casos/{casoId}")
    @PreAuthorize("hasAuthority('PERM_CASOS_VER')")
    public CasoDetalle detalle(
            @PathVariable Long casoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return casoGestionService.detalle(casoId, principal.getUsuario());
    }

    @GetMapping("/api/agentes")
    @PreAuthorize("hasAuthority('PERM_CASOS_ASIGNAR')")
    public List<AgenteOpcion> agentes(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) String area) {
        return casoGestionService.agentes(principal.getUsuario(), area);
    }

    @PostMapping("/api/casos/{casoId}/asignar")
    @PreAuthorize("hasAuthority('PERM_CASOS_ASIGNAR')")
    public AccionCasoRespuesta asignar(
            @PathVariable Long casoId,
            @Valid @RequestBody AsignarRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.asignar(casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PostMapping("/api/casos/{casoId}/reasignar")
    @PreAuthorize("hasAuthority('PERM_CASOS_ASIGNAR')")
    public AccionCasoRespuesta reasignar(
            @PathVariable Long casoId,
            @Valid @RequestBody ReasignarRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.reasignar(casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PatchMapping("/api/casos/{casoId}/estado")
    @PreAuthorize("hasAuthority('PERM_CASOS_GESTIONAR')")
    public AccionCasoRespuesta estado(
            @PathVariable Long casoId,
            @Valid @RequestBody CambioEstadoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.cambiarEstado(
                casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PostMapping("/api/casos/{casoId}/observaciones")
    @PreAuthorize("hasAuthority('PERM_CASOS_GESTIONAR')")
    public AccionCasoRespuesta observacion(
            @PathVariable Long casoId,
            @Valid @RequestBody ObservacionRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.agregarObservacion(
                casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PostMapping("/api/casos/{casoId}/anular")
    @PreAuthorize("hasAuthority('PERM_CASOS_CERRAR')")
    public AccionCasoRespuesta anular(
            @PathVariable Long casoId,
            @Valid @RequestBody AnularRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.anular(casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PostMapping("/api/casos/{casoId}/cerrar")
    @PreAuthorize("hasAuthority('PERM_CASOS_CERRAR')")
    public AccionCasoRespuesta cerrar(
            @PathVariable Long casoId,
            @Valid @RequestBody CerrarCasoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.cerrar(casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PatchMapping("/api/casos/{casoId}")
    @PreAuthorize("hasAuthority('PERM_CASOS_GESTIONAR')")
    public AccionCasoRespuesta modificar(
            @PathVariable Long casoId,
            @Valid @RequestBody ModificarCasoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.modificar(casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PostMapping("/api/casos/{casoId}/prorroga")
    @PreAuthorize("hasAuthority('PERM_CASOS_ASIGNAR')")
    public AccionCasoRespuesta prorroga(
            @PathVariable Long casoId,
            @Valid @RequestBody ProrrogaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.prorrogar(casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PostMapping("/api/casos/{casoId}/escalar")
    @PreAuthorize("hasAuthority('PERM_CASOS_ASIGNAR')")
    public AccionCasoRespuesta escalar(
            @PathVariable Long casoId,
            @Valid @RequestBody EscalarRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        return casoGestionService.escalar(casoId, request, principal.getUsuario(), HttpRequests.ipCliente(http));
    }
}
