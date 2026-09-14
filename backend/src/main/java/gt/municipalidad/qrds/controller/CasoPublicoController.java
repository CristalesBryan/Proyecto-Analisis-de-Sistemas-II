package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.CasoPublicoDtos.CaptchaPublico;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.CasoPublico;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.ListaSeguimientosPublicos;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.RegistroCasoRequest;
import gt.municipalidad.qrds.dto.CasoPublicoDtos.RegistroCasoRespuesta;
import gt.municipalidad.qrds.dto.DocumentoDtos.CargaDocumentosRespuesta;
import gt.municipalidad.qrds.service.CaptchaService;
import gt.municipalidad.qrds.service.CasoPublicoService;
import gt.municipalidad.qrds.service.CasoRegistroService;
import gt.municipalidad.qrds.service.DocumentoService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/casos")
public class CasoPublicoController {

    private final CasoPublicoService casoPublicoService;
    private final CasoRegistroService casoRegistroService;
    private final CaptchaService captchaService;
    private final DocumentoService documentoService;

    public CasoPublicoController(
            CasoPublicoService casoPublicoService,
            CasoRegistroService casoRegistroService,
            CaptchaService captchaService,
            DocumentoService documentoService) {
        this.casoPublicoService = casoPublicoService;
        this.casoRegistroService = casoRegistroService;
        this.captchaService = captchaService;
        this.documentoService = documentoService;
    }

    @GetMapping("/captcha")
    public CaptchaPublico captcha() {
        return captchaService.generar();
    }

    @PostMapping("/publico")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroCasoRespuesta registrar(
            @Valid @RequestBody RegistroCasoRequest request,
            HttpServletRequest http) {
        return casoRegistroService.registrar(request, HttpRequests.ipCliente(http));
    }

    @GetMapping("/publico/{codigo}")
    public CasoPublico consultar(@PathVariable String codigo, HttpServletRequest request) {
        return casoPublicoService.consultar(codigo, HttpRequests.ipCliente(request), HttpRequests.userAgent(request));
    }

    @GetMapping("/publico/{codigo}/seguimientos")
    public ListaSeguimientosPublicos seguimientos(@PathVariable String codigo) {
        return casoPublicoService.seguimientosPublicos(codigo);
    }

    @PostMapping("/publico/{codigo}/documentos")
    public CargaDocumentosRespuesta documentos(
            @PathVariable String codigo,
            @RequestPart("archivos") List<MultipartFile> archivos,
            HttpServletRequest request) {
        return documentoService.adjuntarPublico(codigo, archivos, HttpRequests.ipCliente(request));
    }
}
