package gt.municipalidad.qrds.entity;

public enum TipoResultado {
    ATENDIDO("Atendido"),
    PARCIALMENTE_ATENDIDO("Parcialmente atendido"),
    IMPROCEDENTE("Improcedente"),
    SIN_RESPUESTA_CIUDADANO("Sin respuesta del ciudadano");

    private final String etiqueta;

    TipoResultado(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static TipoResultado de(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
