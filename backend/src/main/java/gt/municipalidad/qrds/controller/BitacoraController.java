package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.AuthDtos.AccesoPublicoRequest;
import gt.municipalidad.qrds.entity.TipoEventoAcceso;
import gt.municipalidad.qrds.service.BitacoraAccesoService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    private final BitacoraAccesoService bitacoraAccesoService;

    public BitacoraController(BitacoraAccesoService bitacoraAccesoService) {
        this.bitacoraAccesoService = bitacoraAccesoService;
    }

    @PostMapping("/acceso-publico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accesoPublico(@RequestBody(required = false) AccesoPublicoRequest request, HttpServletRequest http) {
        String accion = request == null || request.accion() == null || request.accion().isBlank()
                ? "ACCESO_PORTAL"
                : request.accion().trim().toUpperCase();
        String resultado = request == null || request.resultado() == null || request.resultado().isBlank()
                ? "OK"
                : request.resultado().trim();
        TipoEventoAcceso tipo = "CONSULTAR_CASO".equals(accion)
                ? TipoEventoAcceso.CONSULTA_PUBLICA
                : TipoEventoAcceso.ACCESO_PORTAL;
        bitacoraAccesoService.registrar(
                null,
                tipo,
                HttpRequests.ipCliente(http),
                HttpRequests.userAgent(http),
                resultado,
                "Registro de acceso público: " + accion);
    }
}
