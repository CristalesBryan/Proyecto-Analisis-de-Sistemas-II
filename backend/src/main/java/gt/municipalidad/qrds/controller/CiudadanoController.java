package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.CiudadanoDtos.CuentaCiudadano;
import gt.municipalidad.qrds.service.CiudadanoCuentaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ciudadano")
public class CiudadanoController {

    private final CiudadanoCuentaService ciudadanoCuentaService;

    public CiudadanoController(CiudadanoCuentaService ciudadanoCuentaService) {
        this.ciudadanoCuentaService = ciudadanoCuentaService;
    }

    @GetMapping("/cuenta")
    @PreAuthorize("hasRole('CIUDADANO')")
    public CuentaCiudadano cuenta(Authentication authentication) {
        return ciudadanoCuentaService.cuenta(authentication);
    }
}
