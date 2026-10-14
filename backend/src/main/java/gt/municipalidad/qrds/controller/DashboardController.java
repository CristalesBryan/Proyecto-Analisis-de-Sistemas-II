package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.DashboardDtos.ResumenDashboard;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.DashboardService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENTE')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/resumen")
    public ResumenDashboard resumen(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false, defaultValue = "30d") String periodo,
            HttpServletRequest request) {
        return dashboardService.consultar(principal.getUsuario(), periodo, HttpRequests.ipCliente(request));
    }
}
