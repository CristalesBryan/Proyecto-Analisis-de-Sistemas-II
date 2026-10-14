package gt.municipalidad.qrds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class ParametroDtos {

    private ParametroDtos() {
    }

    public record Configuracion(
            List<GrupoParametros> grupos,
            List<AreaParametro> areas,
            List<FeriadoParametro> feriados,
            boolean puedeEditar) {
    }

    public record GrupoParametros(String codigo, String nombre, String ayuda, List<ParametroItem> parametros) {
    }

    public record ParametroItem(
            String clave,
            String etiqueta,
            String valor,
            String ayuda,
            String tipo,
            long version) {
    }

    public record AreaParametro(Long id, String codigo, String nombre, boolean activo, long version, long casos, long usuarios) {
    }

    public record FeriadoParametro(LocalDate fecha, String descripcion) {
    }

    public record ActualizarParametroRequest(@NotBlank String valor, @NotNull Long version) {
    }

    public record ParametroGuardado(String clave, String valorAnterior, String valorNuevo, long version, String mensaje) {
    }

    public record CrearAreaRequest(@NotBlank String codigo, @NotBlank String nombre) {
    }

    public record ActualizarAreaRequest(
            @NotBlank String codigo,
            @NotBlank String nombre,
            @NotNull Boolean activo,
            @NotNull Long version) {
    }

    public record AreaGuardada(AreaParametro area, String mensaje, String advertencia) {
    }

    public record CrearFeriadoRequest(@NotNull LocalDate fecha, @NotBlank String descripcion) {
    }

    public record RecalcularPlazoRequest(@NotBlank String tipo, @NotNull Boolean confirmar) {
    }

    public record RecalculoRespuesta(int casos, String mensaje) {
    }

    public record RestauracionRespuesta(String grupo, String mensaje) {
    }

    public record ImportacionRespuesta(int parametros, int areas, int feriados, String mensaje) {
    }

    public record InstitucionPublica(
            String nombre,
            String direccion,
            String telefono,
            String correo,
            String horario,
            List<PlazoPublico> plazos) {
    }

    public record PlazoPublico(String codigo, String etiqueta, int diasHabiles, String texto) {
    }

    public record Exportacion(
            List<ParametroExportado> parametros,
            List<AreaExportada> areas,
            List<FeriadoParametro> feriados) {
    }

    public record ParametroExportado(String clave, String valor) {
    }

    public record AreaExportada(String codigo, String nombre, boolean activo) {
    }
}
