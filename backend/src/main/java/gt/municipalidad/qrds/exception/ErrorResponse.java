package gt.municipalidad.qrds.exception;

import java.util.Map;

public record ErrorResponse(String mensaje, String codigo, Map<String, String> errores, String codigoExistente) {

    public ErrorResponse(String mensaje, String codigo, Map<String, String> errores) {
        this(mensaje, codigo, errores, null);
    }

    public static ErrorResponse de(String mensaje, String codigo) {
        return new ErrorResponse(mensaje, codigo, null, null);
    }
}
