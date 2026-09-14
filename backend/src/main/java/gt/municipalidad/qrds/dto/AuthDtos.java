package gt.municipalidad.qrds.dto;

import gt.municipalidad.qrds.entity.Permiso;
import gt.municipalidad.qrds.entity.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "El usuario es obligatorio.")
            @Email(message = "Ingresa un correo electrónico válido.")
            String email,
            @NotBlank(message = "La contraseña es obligatoria.")
            String password) {
    }

    public record UsuarioAutenticado(
            Long id,
            Long userId,
            String nombre,
            String email,
            String telefono,
            String dpi,
            String rol,
            String areaDependencia,
            List<String> permisos) {

        public static UsuarioAutenticado de(Usuario usuario) {
            String area = usuario.getAreaDependencia() == null
                    ? null
                    : usuario.getAreaDependencia().getNombre();
            List<String> permisos = Permiso.deRol(usuario.getRol()).stream()
                    .map(Enum::name)
                    .toList();
            return new UsuarioAutenticado(
                    usuario.getId(),
                    usuario.getId(),
                    usuario.getNombre(),
                    usuario.getEmail(),
                    usuario.getTelefono(),
                    usuario.getDpi(),
                    usuario.getRol().name(),
                    area,
                    permisos);
        }
    }

    public record LoginResponse(String token, UsuarioAutenticado usuario) {
    }

    public record SesionResponse(UsuarioAutenticado usuario) {
    }

    public record MensajeResponse(String mensaje) {
    }

    public record AccesoPublicoRequest(String accion, String resultado) {
    }

    public record RegistroCiudadanoRequest(
            @NotBlank(message = "El nombre es obligatorio.")
            String nombre,
            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "Ingrese un correo electrónico válido.")
            String email,
            String telefono,
            @NotBlank(message = "El DPI o CUI es obligatorio.")
            String dpi,
            @NotBlank(message = "La contraseña es obligatoria.")
            String password,
            @NotBlank(message = "Confirme la contraseña.")
            String confirmarPassword,
            boolean aceptaPrivacidad,
            @NotBlank(message = "Resuelva la verificación para continuar.")
            String captchaId,
            @NotBlank(message = "Resuelva la verificación para continuar.")
            String captchaRespuesta) {
    }

    public record RegistroCiudadanoInicioResponse(String registroId, String mensaje) {
    }

    public record ConfirmarRegistroRequest(
            @NotBlank(message = "Falta el identificador de registro.")
            String registroId,
            @NotBlank(message = "Ingrese el código de verificación.")
            String codigo) {
    }
}
