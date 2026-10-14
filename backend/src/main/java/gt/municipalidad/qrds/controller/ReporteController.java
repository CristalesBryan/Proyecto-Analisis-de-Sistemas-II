package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.ReporteDtos.CatalogoReporte;
import gt.municipalidad.qrds.dto.ReporteDtos.GenerarReporteRequest;
import gt.municipalidad.qrds.dto.ReporteDtos.ReporteGeneradoResponse;
import gt.municipalidad.qrds.dto.ReporteDtos.ReporteResumen;
import gt.municipalidad.qrds.entity.FormatoReporte;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.ReporteService;
import gt.municipalidad.qrds.service.ReporteService.Descarga;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class ReporteController {

    private final ReporteService reportes;

    public ReporteController(ReporteService reportes) {
        this.reportes = reportes;
    }

    @GetMapping("/catalogo")
    public List<CatalogoReporte> catalogo(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return reportes.catalogo(principal.getUsuario());
    }

    @PostMapping("/generar")
    public ResponseEntity<ReporteGeneradoResponse> generar(
            @Valid @RequestBody GenerarReporteRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        ReporteGeneradoResponse respuesta = reportes.generar(request, principal.getUsuario(), HttpRequests.ipCliente(http));
        if (respuesta.asincrono()) {
            return ResponseEntity.accepted().body(respuesta);
        }
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/jobs")
    public List<ReporteResumen> jobs(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return reportes.misReportes(principal.getUsuario());
    }

    @GetMapping("/jobs/{id}")
    public ReporteGeneradoResponse job(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return reportes.obtener(id, principal.getUsuario());
    }

    @GetMapping("/jobs/{id}/descarga")
    public ResponseEntity<org.springframework.core.io.Resource> descarga(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest http) {
        Descarga descarga = reportes.descargar(id, principal.getUsuario(), HttpRequests.ipCliente(http));
        MediaType tipo = descarga.formato() == FormatoReporte.PDF
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + descarga.nombre() + "\"")
                .contentType(tipo)
                .body(descarga.recurso());
    }
}
