package gt.municipalidad.qrds.dto;

public final class SistemaDtos {

    private SistemaDtos() {
    }

    public record EstadoSistema(String estado, String mensaje) {
        public static EstadoSistema up() {
            return new EstadoSistema("UP", "Portal público disponible.");
        }

        public static EstadoSistema mantenimiento() {
            return new EstadoSistema("DOWN", "El sistema está en mantenimiento. Intente más tarde.");
        }
    }
}
