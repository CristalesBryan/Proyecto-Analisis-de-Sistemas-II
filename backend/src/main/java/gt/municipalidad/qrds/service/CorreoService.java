package gt.municipalidad.qrds.service;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@Profile("!test")
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);
    private static final String URL = "https://api.resend.com/emails";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final String remitente;
    private final RestClient restClient;

    @Autowired
    public CorreoService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from}") String remitente) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno RESEND_API_KEY. Defínala antes de arrancar la aplicación.");
        }
        if (remitente == null || remitente.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno MAIL_FROM. Defínala antes de arrancar la aplicación.");
        }
        this.apiKey = apiKey.trim();
        this.remitente = remitente.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    protected CorreoService() {
        this.apiKey = "";
        this.remitente = "";
        this.restClient = null;
    }

    @Async
    public void enviar(String para, String asunto, String texto) {
        try {
            enviarInmediato(para, asunto, texto);
        } catch (CorreoNoEnviadoException ex) {
            log.warn("No se pudo enviar el correo a {} (estado {})", destinatario(para), ex.estadoTexto());
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el correo a {} (estado no disponible)", destinatario(para));
        }
    }

    public void enviarInmediato(String para, String asunto, String texto) {
        if (para == null || para.isBlank()) {
            return;
        }
        String destinatario = para.trim();
        try {
            restClient.post()
                    .uri(URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SolicitudResend(remitente, List.of(destinatario), asunto, texto))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Correo enviado a {}", destinatario);
        } catch (RestClientResponseException ex) {
            throw new CorreoNoEnviadoException(ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new CorreoNoEnviadoException(null);
        }
    }

    private static String destinatario(String para) {
        return para == null || para.isBlank() ? "(sin destinatario)" : para.trim();
    }

    private record SolicitudResend(String from, List<String> to, String subject, String text) {
    }

    static final class CorreoNoEnviadoException extends RuntimeException {

        private final Integer estado;

        private CorreoNoEnviadoException(Integer estado) {
            super("No se pudo enviar el correo");
            this.estado = estado;
        }

        String estadoTexto() {
            return estado == null ? "no disponible" : String.valueOf(estado);
        }
    }
}
