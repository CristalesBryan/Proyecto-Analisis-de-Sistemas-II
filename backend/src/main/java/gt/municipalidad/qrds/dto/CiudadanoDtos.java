package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.Usuario;
import java.time.Instant;
import java.util.List;

public final class CiudadanoDtos {

    private CiudadanoDtos() {
    }

    public record PerfilCiudadano(
            Long id,
            String nombre,
            String email,
            String telefono,
            String dpi,
            String rol,
            Instant creadoEn,
            boolean emailVerificado) {

        public static PerfilCiudadano de(Usuario usuario) {
            return new PerfilCiudadano(
                    usuario.getId(),
                    usuario.getNombre(),
                    usuario.getEmail(),
                    usuario.getTelefono(),
                    usuario.getDpi(),
                    usuario.getRol().name(),
                    usuario.getCreadoEn(),
                    usuario.isEmailVerificado());
        }
    }

    public record CasoCiudadano(
            String codigoSeguimiento,
            String tipo,
            String estado,
            Instant fechaRegistro,
            Instant ultimaActualizacion,
            int avancePorcentaje,
            String areaDependencia) {

        public static CasoCiudadano de(Caso caso) {
            String area = caso.getAreaDependencia() == null ? null : caso.getAreaDependencia().getNombre();
            return new CasoCiudadano(
                    caso.getCodigoSeguimiento(),
                    caso.getTipoCaso().getEtiqueta(),
                    caso.getEstado().name(),
                    caso.getFechaRegistro(),
                    caso.getFechaUltimaActualizacion(),
                    caso.getAvancePorcentaje(),
                    area);
        }
    }

    public record CuentaCiudadano(PerfilCiudadano perfil, List<CasoCiudadano> casos) {
    }
}
