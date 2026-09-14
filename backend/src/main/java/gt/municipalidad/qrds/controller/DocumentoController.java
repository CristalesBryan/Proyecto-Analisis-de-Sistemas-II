package gt.municipalidad.qrds.controller;

import gt.municipalidad.qrds.dto.DocumentoDtos.CargaDocumentosRespuesta;
import gt.municipalidad.qrds.dto.DocumentoDtos.DocumentoResumen;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import gt.municipalidad.qrds.service.DocumentoService;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/casos/{casoId}/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_CASOS_VER')")
    public List<DocumentoResumen> listar(
            @PathVariable Long casoId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return documentoService.listar(casoId, principal.getUsuario());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_DOCUMENTOS_CARGAR')")
    public CargaDocumentosRespuesta cargar(
            @PathVariable Long casoId,
            @RequestPart("archivos") List<MultipartFile> archivos,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest request) {
        return documentoService.adjuntarInterno(
                casoId, archivos, principal.getUsuario(), HttpRequests.ipCliente(request));
    }

    @GetMapping("/{documentoId}")
    @PreAuthorize("hasAuthority('PERM_DOCUMENTOS_DESCARGAR')")
    public ResponseEntity<Resource> descargar(
            @PathVariable Long casoId,
            @PathVariable Long documentoId,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            HttpServletRequest request) {
        return documentoService.descargar(
                casoId, documentoId, principal.getUsuario(), HttpRequests.ipCliente(request));
    }
}
