package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.ParametroDtos.ActualizarAreaRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.ActualizarParametroRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.AreaGuardada;
import gt.municipalidad.qrds.dto.ParametroDtos.Configuracion;
import gt.municipalidad.qrds.dto.ParametroDtos.CrearAreaRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.CrearFeriadoRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.Exportacion;
import gt.municipalidad.qrds.dto.ParametroDtos.FeriadoParametro;
import gt.municipalidad.qrds.dto.ParametroDtos.ImportacionRespuesta;
import gt.municipalidad.qrds.dto.ParametroDtos.ParametroGuardado;
import gt.municipalidad.qrds.dto.ParametroDtos.RecalcularPlazoRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.RecalculoRespuesta;
import gt.municipalidad.qrds.dto.ParametroDtos.RestauracionRespuesta;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.ParametroService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/parametros")
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class ParametroController {

    private final ParametroService parametros;

    public ParametroController(ParametroService parametros) {
        this.parametros = parametros;
    }

    @GetMapping
    public Configuracion consultar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return parametros.consultar(principal.getUsuario());
    }

    @PutMapping("/{clave:.+}")
    @PreAuthorize("hasRole('ADMIN')")
    public ParametroGuardado actualizar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable String clave,
            @Valid @RequestBody ActualizarParametroRequest request,
            HttpServletRequest http) {
        return parametros.actualizar(principal.getUsuario(), clave, request, HttpRequests.ipCliente(http));
    }

    @PostMapping("/grupos/{grupo}/restaurar")
    @PreAuthorize("hasRole('ADMIN')")
    public RestauracionRespuesta restaurar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable String grupo,
            HttpServletRequest http) {
        return parametros.restaurar(principal.getUsuario(), grupo, HttpRequests.ipCliente(http));
    }

    @PostMapping("/areas")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AreaGuardada crearArea(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CrearAreaRequest request,
            HttpServletRequest http) {
        return parametros.crearArea(principal.getUsuario(), request, HttpRequests.ipCliente(http));
    }

    @PutMapping("/areas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AreaGuardada actualizarArea(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarAreaRequest request,
            HttpServletRequest http) {
        return parametros.actualizarArea(principal.getUsuario(), id, request, HttpRequests.ipCliente(http));
    }

    @DeleteMapping("/areas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarArea(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest http) {
        parametros.eliminarArea(principal.getUsuario(), id, HttpRequests.ipCliente(http));
    }

    @PostMapping("/feriados")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public FeriadoParametro crearFeriado(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CrearFeriadoRequest request,
            HttpServletRequest http) {
        return parametros.crearFeriado(principal.getUsuario(), request, HttpRequests.ipCliente(http));
    }

    @DeleteMapping("/feriados/{fecha}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarFeriado(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            HttpServletRequest http) {
        parametros.eliminarFeriado(principal.getUsuario(), fecha, HttpRequests.ipCliente(http));
    }

    @PostMapping("/plazos/recalcular")
    @PreAuthorize("hasRole('ADMIN')")
    public RecalculoRespuesta recalcular(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody RecalcularPlazoRequest request,
            HttpServletRequest http) {
        return parametros.recalcular(
                principal.getUsuario(), request.tipo(), Boolean.TRUE.equals(request.confirmar()), HttpRequests.ipCliente(http));
    }

    @GetMapping("/exportar")
    @PreAuthorize("hasRole('ADMIN')")
    public Exportacion exportar(@AuthenticationPrincipal UsuarioPrincipal principal, HttpServletRequest http) {
        return parametros.exportar(principal.getUsuario(), HttpRequests.ipCliente(http));
    }

    @PostMapping("/importar")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportacionRespuesta importar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam MultipartFile archivo,
            HttpServletRequest http) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ARCHIVO_INVALIDO", "Seleccione un archivo.");
        }
        return parametros.importar(principal.getUsuario(), archivo.getBytes(), HttpRequests.ipCliente(http));
    }
}
