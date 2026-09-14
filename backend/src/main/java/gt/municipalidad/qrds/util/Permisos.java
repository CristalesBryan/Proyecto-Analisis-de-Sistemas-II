package gt.municipalidad.qrds.util;

import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class Permisos {

    private Permisos() {
    }

    public static void exigir(Usuario usuario, Permiso permiso) {
        if (usuario == null || !Permiso.deRol(usuario.getRol()).contains(permiso)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
    }
}
