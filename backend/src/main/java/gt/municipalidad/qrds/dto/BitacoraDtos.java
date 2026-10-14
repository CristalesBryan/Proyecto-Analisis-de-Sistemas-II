package gt.municipalidad.qrds.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BitacoraDtos {

    private BitacoraDtos() {
    }

    public record FiltrosBitacora(
            LocalDate desde,
            LocalDate hasta,
            String dominio,
            String tipo,
            Long usuarioId,
            String caso,
            String ip,
            String resultado) {
    }

    public record EventoBitacora(
            String clave,
            String dominio,
            Long id,
            Instant fechaHora,
            String tipoEvento,
            String usuario,
            Long usuarioId,
            String caso,
            Long casoId,
            String descripcion,
            String estadoAnterior,
            String estadoNuevo,
            String ipCliente,
            String resultado,
            String userAgent) {
    }

    public record PaginaBitacora(
            List<EventoBitacora> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            String mensaje) {
    }

    public record ExportarBitacoraRequest(
            @NotNull(message = "Indique los filtros de la exportación.")
            @Valid
            FiltrosBitacora filtros,
            @NotNull(message = "Indique el formato.")
            String formato) {
    }
}
