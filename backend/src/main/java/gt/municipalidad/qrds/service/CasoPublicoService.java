package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CasoPublicoDtos.CasoPublico;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.ListaSeguimientosPublicos;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.SeguimientoPublico;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.TipoEventoAcceso;
import gt.municipalidad.qrds.entity.TipoSeguimiento;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.SeguimientoCasoRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CasoPublicoService {

    private static final Pattern CODIGO_ESTRUCTURADO = Pattern.compile("^[QRDS]-\\d{4}-\\d{5}$");
    private static final Pattern CODIGO_CORTO = Pattern.compile("^[A-Z0-9]{10}$");

    private final CasoRepository casoRepository;
    private final SeguimientoCasoRepository seguimientoCasoRepository;
    private final BitacoraAccesoService bitacoraAccesoService;

    public CasoPublicoService(
            CasoRepository casoRepository,
            SeguimientoCasoRepository seguimientoCasoRepository,
            BitacoraAccesoService bitacoraAccesoService) {
        this.casoRepository = casoRepository;
        this.seguimientoCasoRepository = seguimientoCasoRepository;
        this.bitacoraAccesoService = bitacoraAccesoService;
    }

    @Transactional(readOnly = true)
    public CasoPublico consultar(String codigo, String ip, String userAgent) {
        Caso caso = localizarPublico(codigo, ip, userAgent, true);
        return CasoPublico.de(caso, seguimientosVisibles(caso));
    }

    @Transactional(readOnly = true)
    public ListaSeguimientosPublicos seguimientosPublicos(String codigo) {
        Caso caso = localizarPublico(codigo, null, null, false);
        return new ListaSeguimientosPublicos(
                caso.getCodigoSeguimiento(), caso.getAvancePorcentaje(), seguimientosVisibles(caso));
    }

    private Caso localizarPublico(String codigo, String ip, String userAgent, boolean auditar) {
        String valor = normalizar(codigo);
        if (!formatoValido(valor)) {
            if (auditar) {
                bitacoraAccesoService.registrar(
                        null, TipoEventoAcceso.CONSULTA_PUBLICA, ip, userAgent, "DATOS_INVALIDOS",
                        "Consulta pública con identificador inválido.");
            }
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CODIGO_INVALIDO",
                    "Los datos ingresados no permiten realizar la consulta.");
        }
        Caso caso = casoRepository.findByCodigoSeguimientoIgnoreCase(valor).orElse(null);
        if (caso == null) {
            if (auditar) {
                bitacoraAccesoService.registrar(
                        null, TipoEventoAcceso.CONSULTA_PUBLICA, ip, userAgent, "NO_ENCONTRADO",
                        "Consulta pública de código inexistente.");
            }
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CASO_NO_ENCONTRADO",
                    "Código no encontrado. Verifique el número e intente nuevamente.");
        }
        if (auditar) {
            bitacoraAccesoService.registrar(
                    null, TipoEventoAcceso.CONSULTA_PUBLICA, ip, userAgent, "ENCONTRADO",
                    "Consulta pública de caso " + caso.getCodigoSeguimiento() + ".");
        }
        return caso;
    }

    private List<SeguimientoPublico> seguimientosVisibles(Caso caso) {
        return seguimientoCasoRepository
                .findByCasoAndTipoOrderByCreadoEnDesc(caso, TipoSeguimiento.PUBLICA)
                .stream()
                .map(SeguimientoPublico::de)
                .toList();
    }

    private String normalizar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase();
    }

    private boolean formatoValido(String codigo) {
        return CODIGO_ESTRUCTURADO.matcher(codigo).matches() || CODIGO_CORTO.matcher(codigo).matches();
    }
}
