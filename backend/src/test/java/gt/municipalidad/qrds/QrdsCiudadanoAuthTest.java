package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrdsCiudadanoAuthTest {

    private static final Pattern SUMA = Pattern.compile("(\\d+) \\+ (\\d+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ciudadanoDemoIniciaSesionYVeSusDatosYCasos() throws Exception {
        String token = tokenDe("ciudadano@email.com");

        mockMvc.perform(get("/api/ciudadano/cuenta").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.nombre").value("Ciudadano Demo"))
                .andExpect(jsonPath("$.perfil.email").value("ciudadano@email.com"))
                .andExpect(jsonPath("$.perfil.telefono").value("55551234"))
                .andExpect(jsonPath("$.perfil.dpi").value("1234567890101"))
                .andExpect(jsonPath("$.perfil.rol").value("CIUDADANO"))
                .andExpect(jsonPath("$.casos[0].codigoSeguimiento").value("Q-2026-00001"));
    }

    @Test
    void ciudadanoNoAccedeALaBandejaInterna() throws Exception {
        String token = tokenDe("ciudadano@email.com");
        mockMvc.perform(get("/api/casos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void registroRechazaCaptchaInvalido() throws Exception {
        Captcha captcha = captcha();
        mockMvc.perform(post("/api/auth/ciudadano/verificar-inicio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadRegistro(
                                "Ana Pérez",
                                "ana.nueva@email.com",
                                "2999123456789",
                                DataInitializer.PASSWORD_DEMO,
                                captcha.id(),
                                "999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CAPTCHA_INVALIDO"));
    }

    @Test
    void registroRechazaPasswordDebil() throws Exception {
        Captcha captcha = captcha();
        mockMvc.perform(post("/api/auth/ciudadano/verificar-inicio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadRegistro(
                                "Ana Pérez",
                                "ana.clave@email.com",
                                "2999123456788",
                                "clave",
                                captcha.id(),
                                captcha.respuesta())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void ciudadanoSeRegistraConVerificacionesEIniciaSesion() throws Exception {
        Captcha captcha = captcha();
        MvcResult inicio = mockMvc.perform(post("/api/auth/ciudadano/verificar-inicio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadRegistro(
                                "María López",
                                "maria.lopez@email.com",
                                "2599123456789",
                                DataInitializer.PASSWORD_DEMO,
                                captcha.id(),
                                captcha.respuesta())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registroId").isNotEmpty())
                .andReturn();

        String registroId = objectMapper.readTree(inicio.getResponse().getContentAsString())
                .get("registroId")
                .asText();

        mockMvc.perform(post("/api/auth/ciudadano/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registroId":"%s","codigo":"000000"}
                                """.formatted(registroId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));

        mockMvc.perform(post("/api/auth/ciudadano/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registroId":"%s","codigo":"123456"}
                                """.formatted(registroId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "Cuenta creada. Ya puede iniciar sesión con su correo y contraseña."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"maria.lopez@email.com","password":"%s"}
                                """.formatted(DataInitializer.PASSWORD_DEMO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("CIUDADANO"))
                .andExpect(jsonPath("$.usuario.nombre").value("María López"))
                .andExpect(jsonPath("$.usuario.dpi").value("2599123456789"));
    }

    private String tokenDe(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, DataInitializer.PASSWORD_DEMO)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Captcha captcha() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/casos/captcha"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Matcher matcher = SUMA.matcher(body.get("pregunta").asText());
        if (!matcher.find()) {
            throw new IllegalStateException("No se pudo interpretar el captcha");
        }
        int suma = Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2));
        return new Captcha(body.get("captchaId").asText(), String.valueOf(suma));
    }

    private String payloadRegistro(
            String nombre,
            String email,
            String dpi,
            String password,
            String captchaId,
            String captchaRespuesta) {
        return """
                {
                  "nombre":"%s",
                  "email":"%s",
                  "telefono":"55559876",
                  "dpi":"%s",
                  "password":"%s",
                  "confirmarPassword":"%s",
                  "aceptaPrivacidad":true,
                  "captchaId":"%s",
                  "captchaRespuesta":"%s"
                }
                """.formatted(nombre, email, dpi, password, password, captchaId, captchaRespuesta);
    }

    private record Captcha(String id, String respuesta) {
    }
}
