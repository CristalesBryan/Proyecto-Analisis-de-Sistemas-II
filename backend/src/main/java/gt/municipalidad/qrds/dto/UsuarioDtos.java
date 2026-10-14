package gt.municipalidad.qrds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record AreaOpcion(Long id, String codigo, String nombre) {
        public static AreaOpcion de(AreaDependencia area) {
            return new AreaOpcion(area.getId(), area.getCodigo(), area.getNombre());
        }
    }

    public record UsuarioResumen(
            Long id,
            String nombre,
            String email,
            String rol,
            Long areaId,
            String areaCodigo,
            String areaNombre,
            boolean activo,
            boolean forzarCambioPassword,
            Instant creadoEn,
            Instant actualizadoEn) {

        public static UsuarioResumen de(Usuario usuario) {
            AreaDependencia area = usuario.getAreaDependencia();
            return new UsuarioResumen(
                    usuario.getId(),
                    usuario.getNombre(),
                    usuario.getEmail(),
                    usuario.getRol().name(),
                    area == null ? null : area.getId(),
                    area == null ? null : area.getCodigo(),
                    area == null ? null : area.getNombre(),
                    usuario.isActivo(),
                    usuario.isForzarCambioPassword(),
                    usuario.getCreadoEn(),
                    usuario.getActualizadoEn());
        }
    }

    public record PaginaUsuarios(
            List<UsuarioResumen> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record CrearUsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio.")
            @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
            String nombre,
            @NotBlank(message = "El correo es obligatorio.")
            String email,
            @NotBlank(message = "El rol es obligatorio.")
            String rol,
            Long areaId,
            String password,
            Boolean generarPassword,
            Boolean activo) {
    }

    public record ActualizarUsuarioRequest(
            @NotBlank(message = "El nombre es obligatorio.")
            @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
            String nombre,
            @NotBlank(message = "El correo es obligatorio.")
            String email,
            @NotBlank(message = "El rol es obligatorio.")
            String rol,
            Long areaId,
            @NotNull(message = "El estado es obligatorio.")
            Boolean activo) {
    }

    public record ResetPasswordRequest(String nuevaPassword) {
    }

    public record CambiarPasswordRequest(
            @NotBlank(message = "La contraseña actual es obligatoria.")
            String actual,
            @NotBlank(message = "La nueva contraseña es obligatoria.")
            String nueva,
            @NotBlank(message = "Confirme la contraseña.")
            String confirmacion) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UsuarioAccionResponse(String mensaje, UsuarioResumen usuario, String contrasenaTemporal) {
    }

    public record CambiarPasswordResponse(
            String mensaje,
            String token,
            AuthDtos.UsuarioAutenticado usuario) {
    }
}
