package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CasoPublicoDtos {

    private CasoPublicoDtos() {
    }

    public record CaptchaPublico(String captchaId, String pregunta) {
    }

    public record RegistroCasoRequest(
            @NotNull(message = "Seleccione un tipo de caso.")
            TipoCaso tipoCaso,
            String nombreCiudadano,
            String email,
            String telefono,
            String areaDependencia,
            @NotBlank(message = "La descripción es obligatoria.")
            @Size(min = 50, max = 2000, message = "La descripción debe tener entre 50 y 2000 caracteres.")
            String descripcion,
            String denunciado,
            boolean esAnonimo,
            boolean aceptaPrivacidad,
            @NotBlank(message = "Resuelva la verificación para continuar.")
            String captchaId,
            @NotBlank(message = "Resuelva la verificación para continuar.")
            String captchaRespuesta,
            boolean forzarRegistro) {
    }

    public record RegistroCasoRespuesta(
            String codigoSeguimiento,
            String mensaje,
            Instant fechaRegistro,
            String tipoCaso,
            String tipo,
            String estado,
            String plazoEstimado,
            boolean correoEnviado,
            boolean esAnonimo) {

        public static RegistroCasoRespuesta de(Caso caso, boolean correoEnviado) {
            return new RegistroCasoRespuesta(
                    caso.getCodigoSeguimiento(),
                    "Caso registrado correctamente. Guarde su código de seguimiento.",
                    caso.getFechaRegistro(),
                    caso.getTipoCaso().name(),
                    caso.getTipoCaso().getEtiqueta(),
                    caso.getEstado().name(),
                    caso.getTipoCaso().plazoEstimado(),
                    correoEnviado,
                    caso.isEsAnonimo());
        }
    }

    public record CasoPublico(
            String codigoSeguimiento,
            String tipoCaso,
            String tipo,
            String estado,
            Instant fechaRegistro,
            Instant ultimaActualizacion,
            int avancePorcentaje,
            List<SeguimientoPublico> seguimientos) {

        public static CasoPublico de(Caso caso, List<SeguimientoPublico> seguimientos) {
            return new CasoPublico(
                    caso.getCodigoSeguimiento(),
                    caso.getTipoCaso().name(),
                    caso.getTipoCaso().getEtiqueta(),
                    caso.getEstado().name(),
                    caso.getFechaRegistro(),
                    caso.getFechaUltimaActualizacion(),
                    caso.getAvancePorcentaje(),
                    seguimientos);
        }
    }

    public record SeguimientoPublico(
            Long id,
            String tipo,
            String titulo,
            String descripcion,
            Integer porcentajeAvance,
            Instant creadoEn) {

        public static SeguimientoPublico de(SeguimientoCaso seguimiento) {
            return new SeguimientoPublico(
                    seguimiento.getId(),
                    seguimiento.getTipo().name(),
                    seguimiento.getTitulo(),
                    seguimiento.getDescripcion(),
                    seguimiento.getPorcentajeAvance(),
                    seguimiento.getCreadoEn());
        }
    }

    public record ListaSeguimientosPublicos(
            String codigoSeguimiento,
            int avancePorcentaje,
            List<SeguimientoPublico> seguimientos) {
    }

    public record AreaPublica(String codigo, String nombre) {
    }
}
