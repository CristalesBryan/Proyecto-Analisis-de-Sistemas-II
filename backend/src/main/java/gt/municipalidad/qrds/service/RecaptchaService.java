package gt.municipalidad.qrds.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import gt.municipalidad.qrds.exception.ApiException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class RecaptchaService {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String secret;
    private final boolean verificarGoogle;
    private final RestClient restClient;

    public RecaptchaService(
            @Value("${recaptcha.secret}") String secret,
            @Value("${recaptcha.verificar-google:true}") boolean verificarGoogle) {
        this.secret = secret == null ? "" : secret.trim();
        this.verificarGoogle = verificarGoogle;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public void verificar(String token) {
        if (token == null || token.isBlank()) {
            throw invalido("Complete la verificación «No soy un robot».");
        }
        if (!verificarGoogle) {
            return;
        }
        if (secret.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CAPTCHA_NO_CONFIGURADO",
                    "La verificación anti-bot no está configurada. Intente más tarde.");
        }

        GoogleRespuesta respuesta;
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secret);
            form.add("response", token.trim());
            respuesta = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleRespuesta.class);
        } catch (ResourceAccessException ex) {
            throw noDisponible();
        } catch (RestClientException ex) {
            throw noDisponible();
        }

        if (respuesta == null || !respuesta.success()) {
            throw invalido("La verificación «No soy un robot» no es válida o expiró. Inténtelo de nuevo.");
        }
    }

    private ApiException invalido(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "CAPTCHA_INVALIDO", mensaje);
    }

    private ApiException noDisponible() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CAPTCHA_NO_DISPONIBLE",
                "No fue posible validar el captcha. Verifique su conexión e intente nuevamente.");
    }

    private record GoogleRespuesta(
            boolean success,
            String hostname,
            @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
