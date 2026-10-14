package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.dto.CasoPublicoDtos.SeguimientoPublico;
import java.time.Instant;
import java.util.List;

public final class HistorialDtos {

    private HistorialDtos() {
    }

    public record EventoHistorial(
            long id,
            String tipo,
            String categoria,
            String titulo,
            String resumen,
            String detalle,
            Instant fechaHora,
            String autor,
            boolean interno,
            Long documentoId) {
    }

    public record PaginaHistorial(
            List<EventoHistorial> eventos,
            int page,
            int size,
            long total,
            boolean parcial,
            String aviso) {
    }

    public record VistaCiudadano(
            String codigoSeguimiento,
            String tipo,
            String estado,
            Instant fechaRegistro,
            Instant ultimaActualizacion,
            int avancePorcentaje,
            List<SeguimientoPublico> seguimientos) {
    }
}
