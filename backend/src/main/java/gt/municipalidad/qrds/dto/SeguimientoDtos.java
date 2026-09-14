package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.dto.CasoDtos.CasoDetalle;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import java.time.Instant;
import java.util.List;

public final class SeguimientoDtos {

    private SeguimientoDtos() {
    }

    public record AdjuntoSeguimiento(Long id, String nombreArchivo) {
    }

    public record SeguimientoInterno(
            Long id,
            String tipo,
            String titulo,
            String descripcion,
            Integer porcentajeAvance,
            Instant creadoEn,
            Long seguimientoPadreId,
            boolean notificado,
            String usuarioNombre,
            Long usuarioId,
            AdjuntoSeguimiento adjunto) {

        public static SeguimientoInterno de(SeguimientoCaso seguimiento) {
            Usuario autor = seguimiento.getUsuario();
            AdjuntoSeguimiento adjunto = seguimiento.getNombreArchivo() == null
                    ? null
                    : new AdjuntoSeguimiento(seguimiento.getId(), seguimiento.getNombreArchivo());
            return new SeguimientoInterno(
                    seguimiento.getId(),
                    seguimiento.getTipo().name(),
                    seguimiento.getTitulo(),
                    seguimiento.getDescripcion(),
                    seguimiento.getPorcentajeAvance(),
                    seguimiento.getCreadoEn(),
                    seguimiento.getSeguimientoPadre() == null ? null : seguimiento.getSeguimientoPadre().getId(),
                    seguimiento.isNotificado(),
                    autor == null ? null : autor.getNombre(),
                    autor == null ? null : autor.getId(),
                    adjunto);
        }
    }

    public record ListaSeguimientosInternos(
            String codigoSeguimiento,
            String estado,
            int avancePorcentaje,
            List<SeguimientoInterno> seguimientos) {
    }

    public record RegistroSeguimientoRespuesta(
            String mensaje,
            String avisoCorreo,
            SeguimientoInterno seguimiento,
            CasoDetalle caso,
            List<SeguimientoInterno> seguimientos) {
    }
}
