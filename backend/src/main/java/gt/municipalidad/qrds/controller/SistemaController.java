package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.SistemaDtos.EstadoSistema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sistema")
public class SistemaController {

    private final boolean mantenimiento;

    public SistemaController(@Value("${qrds.portal.mantenimiento}") boolean mantenimiento) {
        this.mantenimiento = mantenimiento;
    }

    @GetMapping("/estado")
    public EstadoSistema estado() {
        return mantenimiento ? EstadoSistema.mantenimiento() : EstadoSistema.up();
    }
}
