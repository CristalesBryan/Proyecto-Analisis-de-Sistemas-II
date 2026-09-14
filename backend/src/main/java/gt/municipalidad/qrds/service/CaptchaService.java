package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CasoPublicoDtos.CaptchaPublico;
import gt.municipalidad.qrds.exception.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private static final long TTL_SEGUNDOS = 10 * 60;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Reto> retos = new ConcurrentHashMap<>();

    public CaptchaPublico generar() {
        limpiarExpirados();
        int a = 1 + random.nextInt(12);
        int b = 1 + random.nextInt(12);
        String id = UUID.randomUUID().toString();
        retos.put(id, new Reto(a + b, Instant.now().plusSeconds(TTL_SEGUNDOS)));
        return new CaptchaPublico(id, "¿Cuánto es " + a + " + " + b + "?");
    }

    public void verificar(String captchaId, String respuesta) {
        limpiarExpirados();
        Reto reto = captchaId == null ? null : retos.remove(captchaId.trim());
        if (reto == null || Instant.now().isAfter(reto.expira())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CAPTCHA_INVALIDO",
                    "La verificación expiró o es incorrecta. Intente nuevamente.");
        }
        int valor;
        try {
            valor = Integer.parseInt(respuesta == null ? "" : respuesta.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CAPTCHA_INVALIDO",
                    "La verificación expiró o es incorrecta. Intente nuevamente.");
        }
        if (valor != reto.respuesta()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CAPTCHA_INVALIDO",
                    "La verificación expiró o es incorrecta. Intente nuevamente.");
        }
    }

    private void limpiarExpirados() {
        Instant ahora = Instant.now();
        retos.entrySet().removeIf(entry -> ahora.isAfter(entry.getValue().expira()));
    }

    private record Reto(int respuesta, Instant expira) {
    }
}
