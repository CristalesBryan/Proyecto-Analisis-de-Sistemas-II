package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.entity.FormatoReporte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReporteDtos {

    private ReporteDtos() {
    }

    public record CatalogoReporte(String codigo, String nombre, String descripcion, List<String> formatos) {
    }

    public record FiltrosReporteRequest(
            @NotNull LocalDate fechaDesde,
            @NotNull LocalDate fechaHasta,
            String tipoCaso,
            String estado,
            String area,
            Long agenteId,
            Boolean incluirAnulados) {
    }

    public record GenerarReporteRequest(
            @NotBlank String tipo,
            @NotNull FormatoReporte formato,
            @NotNull @Valid FiltrosReporteRequest filtros) {
    }

    public record ReporteGeneradoResponse(
            Long id,
            String tipo,
            String nombre,
            String formato,
            String estado,
            int cantidadFilas,
            boolean asincrono,
            boolean reutilizado,
            String mensaje,
            List<String> columnas,
            List<List<String>> filas,
            Instant creadoEn,
            Instant expiraEn,
            boolean descargable) {
    }

    public record ReporteResumen(
            Long id,
            String tipo,
            String nombre,
            String formato,
            String estado,
            Integer cantidadFilas,
            Instant creadoEn,
            Instant expiraEn,
            boolean descargable,
            String errorMensaje) {
    }
}
