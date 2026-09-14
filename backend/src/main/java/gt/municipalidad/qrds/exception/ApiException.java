package gt.municipalidad.qrds.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final Map<String, String> errores;
    private final String codigoExistente;

    public ApiException(HttpStatus status, String codigo, String mensaje) {
        this(status, codigo, mensaje, null, null);
    }

    public ApiException(HttpStatus status, String codigo, String mensaje, Map<String, String> errores) {
        this(status, codigo, mensaje, errores, null);
    }

    public ApiException(
            HttpStatus status,
            String codigo,
            String mensaje,
            Map<String, String> errores,
            String codigoExistente) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
        this.errores = errores;
        this.codigoExistente = codigoExistente;
    }

    public static ApiException casoSimilar(String codigoExistente) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "CASO_SIMILAR",
                "Detectamos que puede tener un caso similar registrado recientemente.",
                null,
                codigoExistente);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, String> getErrores() {
        return errores;
    }

    public String getCodigoExistente() {
        return codigoExistente;
    }
}
