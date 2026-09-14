package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.SeguimientoDtos.ListaSeguimientosInternos;
import gt.municipalidad.qrds.dto.SeguimientoDtos.SeguimientoInterno;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.TipoSeguimiento;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.SeguimientoCasoRepository;
import gt.municipalidad.qrds.util.Permisos;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeguimientoConsultaService {

    private final CasoRepository casoRepository;
    private final SeguimientoCasoRepository seguimientoCasoRepository;
    private final BitacoraCasoService bitacoraCasoService;

    public SeguimientoConsultaService(
            CasoRepository casoRepository,
            SeguimientoCasoRepository seguimientoCasoRepository,
            BitacoraCasoService bitacoraCasoService) {
        this.casoRepository = casoRepository;
        this.seguimientoCasoRepository = seguimientoCasoRepository;
        this.bitacoraCasoService = bitacoraCasoService;
    }

    @Transactional
    public ListaSeguimientosInternos listar(Long casoId, boolean incluirInternos, Usuario usuario, String ip) {
        Permisos.exigir(usuario, Permiso.CASOS_VER);
        Caso caso = casoRepository.findById(casoId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "CASO_NO_ENCONTRADO",
                "El caso solicitado no existe."));
        List<SeguimientoInterno> items = seguimientoCasoRepository.findByCasoOrderByCreadoEnDesc(caso).stream()
                .filter(item -> incluirInternos || item.getTipo() == TipoSeguimiento.PUBLICA)
                .map(SeguimientoInterno::de)
                .toList();
        bitacoraCasoService.registrar(
                caso,
                usuario,
                TipoEventoCaso.CONSULTA_SEGUIMIENTO,
                "Consulta interna de seguimiento del caso " + caso.getCodigoSeguimiento() + ".",
                ip);
        return new ListaSeguimientosInternos(
                caso.getCodigoSeguimiento(),
                caso.getEstado().name(),
                caso.getAvancePorcentaje(),
                items);
    }
}
