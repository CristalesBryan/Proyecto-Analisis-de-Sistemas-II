package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.dto.DocumentoDtos.DocumentoResumen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CasoDtos {

    private CasoDtos() {
    }

    public record PlazoCaso(Integer diasRestantes, String semaforo, boolean vencido) {
    }

    public record CasoResumen(
            Long id,
            String codigoSeguimiento,
            String tipoCaso,
            String tipo,
            String ciudadano,
            String area,
            String areaNombre,
            String estado,
            Instant fechaRegistro,
            Long agenteAsignadoId,
            String agenteNombre,
            String prioridad,
            int avancePorcentaje,
            boolean escalado,
            LocalDate fechaLimiteRespuesta,
            PlazoCaso plazo) {
    }

    public record ObservacionResumen(Long id, String texto, Instant creadoEn, String usuarioNombre) {
    }

    public record HistorialResumen(
            String tipoEvento,
            String estadoAnterior,
            String estadoNuevo,
            String descripcion,
            Instant fechaHora,
            String usuarioNombre) {
    }

    public record CasoDetalle(
            Long id,
            String codigoSeguimiento,
            String tipoCaso,
            String tipo,
            String ciudadano,
            String area,
            String areaNombre,
            String estado,
            Instant fechaRegistro,
            Long agenteAsignadoId,
            String agenteNombre,
            String prioridad,
            int avancePorcentaje,
            boolean escalado,
            LocalDate fechaLimiteRespuesta,
            PlazoCaso plazo,
            String nombreCiudadano,
            String emailCiudadano,
            String telefono,
            boolean esAnonimo,
            String descripcion,
            String denunciado,
            List<String> transicionesPermitidas,
            Instant fechaUltimaActualizacion,
            List<DocumentoResumen> documentos,
            List<ObservacionResumen> observaciones,
            List<HistorialResumen> historial,
            LocalDate fechaProrroga) {
    }

    public record PaginaCasos(
            List<CasoResumen> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record AgenteOpcion(Long id, String nombre, String email, String areaDependencia, String areaNombre) {
    }

    public record AccionCasoRespuesta(String mensaje, CasoDetalle caso) {
    }

    public record AsignarRequest(@NotNull(message = "Seleccione un agente.") Long agenteId) {
    }

    public record ReasignarRequest(
            @NotNull(message = "Seleccione un agente.") Long agenteId,
            @NotBlank(message = "Indique el motivo de la reasignación.")
            @Size(min = 10, message = "El motivo debe tener al menos 10 caracteres.")
            String motivo) {
    }

    public record CambioEstadoRequest(
            @NotBlank(message = "Seleccione el nuevo estado.") String nuevoEstado,
            @NotBlank(message = "La observación es obligatoria.")
            @Size(min = 10, message = "La observación debe tener al menos 10 caracteres.")
            String observacion) {
    }

    public record ObservacionRequest(
            @NotBlank(message = "La observación es obligatoria.")
            @Size(min = 10, message = "La observación debe tener al menos 10 caracteres.")
            String texto) {
    }

    public record AnularRequest(
            @NotBlank(message = "La justificación es obligatoria.")
            @Size(min = 20, message = "La justificación debe tener al menos 20 caracteres.")
            String justificacion) {
    }

    public record CerrarCasoRequest(
            @NotBlank(message = "Documente el cierre del caso.")
            @Size(min = 20, message = "La observación de cierre debe tener al menos 20 caracteres.")
            String observacion) {
    }

    public record ModificarCasoRequest(
            String nombreCiudadano,
            String email,
            String telefono,
            String areaDependencia,
            String descripcion,
            String denunciado,
            String prioridad,
            @NotBlank(message = "Indique el motivo de la modificación.")
            @Size(min = 10, message = "El motivo debe tener al menos 10 caracteres.")
            String motivo) {
    }

    public record ProrrogaRequest(
            @NotNull(message = "Indique los días hábiles de prórroga.") Integer diasHabiles,
            @NotBlank(message = "La justificación es obligatoria.")
            @Size(min = 20, message = "La justificación debe tener al menos 20 caracteres.")
            String justificacion) {
    }

    public record EscalarRequest(
            @NotBlank(message = "Indique el motivo del escalamiento.")
            @Size(min = 10, message = "El motivo debe tener al menos 10 caracteres.")
            String motivo) {
    }
}
