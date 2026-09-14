package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.SeguimientoDtos.ListaSeguimientosInternos;
import gt.municipalidad.qrds.dto.SeguimientoDtos.RegistroSeguimientoRespuesta;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.SeguimientoConsultaService;
import gt.municipalidad.qrds.service.SeguimientoRegistroService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/casos/{casoId}/seguimientos")
public class SeguimientoController {

    private final SeguimientoConsultaService seguimientoConsultaService;
    private final SeguimientoRegistroService seguimientoRegistroService;

    public SeguimientoController(
            SeguimientoConsultaService seguimientoConsultaService,
            SeguimientoRegistroService seguimientoRegistroService) {
        this.seguimientoConsultaService = seguimientoConsultaService;
        this.seguimientoRegistroService = seguimientoRegistroService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_CASOS_VER')")
    public ListaSeguimientosInternos listar(
            @PathVariable Long casoId,
            @RequestParam(defaultValue = "true") boolean incluirInternos,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest request) {
        return seguimientoConsultaService.listar(
                casoId, incluirInternos, principal.getUsuario(), HttpRequests.ipCliente(request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CASOS_GESTIONAR')")
    public RegistroSeguimientoRespuesta registrar(
            @PathVariable Long casoId,
            @RequestParam String tipo,
            @RequestParam String titulo,
            @RequestParam String descripcion,
            @RequestParam(required = false) String porcentajeAvance,
            @RequestParam(required = false) String notificarCiudadano,
            @RequestParam(required = false) String seguimientoPadreId,
            @RequestParam(required = false) String justificacionExcepcional,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest request) {
        return seguimientoRegistroService.registrar(
                casoId,
                tipo,
                titulo,
                descripcion,
                porcentajeAvance,
                notificarCiudadano,
                seguimientoPadreId,
                justificacionExcepcional,
                archivo,
                principal.getUsuario(),
                HttpRequests.ipCliente(request));
    }

    @GetMapping("/{seguimientoId}/documento")
    @PreAuthorize("hasAuthority('PERM_DOCUMENTOS_DESCARGAR')")
    public ResponseEntity<Resource> descargar(
            @PathVariable Long casoId,
            @PathVariable Long seguimientoId,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest request) {
        return seguimientoRegistroService.descargar(
                casoId, seguimientoId, principal.getUsuario(), HttpRequests.ipCliente(request));
    }
}
