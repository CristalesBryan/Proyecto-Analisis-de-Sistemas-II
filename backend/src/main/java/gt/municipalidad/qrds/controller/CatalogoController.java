package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.CasoPublicoDtos.AreaPublica;
import gt.municipalidad.qrds.dto.ParametroDtos.InstitucionPublica;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.service.ParametroService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final AreaDependenciaRepository areaDependenciaRepository;
    private final ParametroService parametroService;

    public CatalogoController(AreaDependenciaRepository areaDependenciaRepository, ParametroService parametroService) {
        this.areaDependenciaRepository = areaDependenciaRepository;
        this.parametroService = parametroService;
    }

    @GetMapping("/institucion")
    public InstitucionPublica institucion() {
        return parametroService.institucion();
    }

    @GetMapping("/areas")
    public List<AreaPublica> areas() {
        return areaDependenciaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(area -> new AreaPublica(area.getCodigo(), area.getNombre()))
                .toList();
    }
}
