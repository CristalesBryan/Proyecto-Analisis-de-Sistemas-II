package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CiudadanoDtos.CasoCiudadano;
import gt.municipalidad.qrds.dto.CiudadanoDtos.CuentaCiudadano;
import gt.municipalidad.qrds.dto.CiudadanoDtos.PerfilCiudadano;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import gt.municipalidad.qrds.security.UsuarioPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CiudadanoCuentaService {

    private final UsuarioRepository usuarioRepository;
    private final CasoRepository casoRepository;

    public CiudadanoCuentaService(UsuarioRepository usuarioRepository, CasoRepository casoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.casoRepository = casoRepository;
    }

    @Transactional(readOnly = true)
    public CuentaCiudadano cuenta(Authentication authentication) {
        Usuario usuario = ciudadanoDe(authentication);
        List<CasoCiudadano> casos = casoRepository
                .findByEmailCiudadanoIgnoreCaseAndEsAnonimoFalseOrderByFechaRegistroDesc(usuario.getEmail())
                .stream()
                .map(CasoCiudadano::de)
                .toList();
        return new CuentaCiudadano(PerfilCiudadano.de(usuario), casos);
    }

    private Usuario ciudadanoDe(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "TOKEN_INVALIDO",
                    "Su sesión ha expirado. Por favor inicie sesión nuevamente.");
        }
        Usuario usuario = usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "TOKEN_INVALIDO",
                "Su sesión ha expirado. Por favor inicie sesión nuevamente."));
        if (usuario.getRol() != Rol.CIUDADANO) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "Esta sección es solo para ciudadanos registrados.");
        }
        return usuario;
    }
}
