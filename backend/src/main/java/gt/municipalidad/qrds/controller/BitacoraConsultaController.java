package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.BitacoraDtos.EventoBitacora;
import gt.municipalidad.qrds.dto.BitacoraDtos.ExportarBitacoraRequest;
import gt.municipalidad.qrds.dto.BitacoraDtos.FiltrosBitacora;
import gt.municipalidad.qrds.dto.BitacoraDtos.PaginaBitacora;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.BitacoraConsultaService;
import gt.municipalidad.qrds.service.BitacoraConsultaService.Archivo;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bitacoras")
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class BitacoraConsultaController {

    private final BitacoraConsultaService bitacoras;

    public BitacoraConsultaController(BitacoraConsultaService bitacoras) {
        this.bitacoras = bitacoras;
    }

    @GetMapping
    public PaginaBitacora consultar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String dominio,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String caso,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String resultado,
            @RequestParam(defaultValue = "0") int page,
            HttpServletRequest http) {
        return bitacoras.consultar(
                principal.getUsuario(),
                new FiltrosBitacora(desde, hasta, dominio, tipo, usuarioId, caso, ip, resultado),
                page,
                HttpRequests.ipCliente(http));
    }

    @GetMapping("/{dominio}/{id}")
    public EventoBitacora detalle(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable String dominio,
            @PathVariable Long id) {
        return bitacoras.detalle(principal.getUsuario(), dominio, id);
    }

    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody ExportarBitacoraRequest request,
            HttpServletRequest http) {
        Archivo archivo = bitacoras.exportar(
                principal.getUsuario(),
                request.filtros(),
                request.formato(),
                HttpRequests.ipCliente(http));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombre() + "\"")
                .contentType(archivo.tipo())
                .body(archivo.contenido());
    }

    @RequestMapping(value = {"", "/{dominio}/{id}"}, method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public void soloLectura() {
        throw new ApiException(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METODO_NO_PERMITIDO",
                "Los registros de bitácora son de solo lectura.");
    }
}
