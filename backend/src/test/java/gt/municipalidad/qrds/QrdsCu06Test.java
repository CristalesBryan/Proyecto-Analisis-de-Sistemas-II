package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class QrdsCu06Test {

    private static final Pattern SUMA = Pattern.compile("(\\d+) \\+ (\\d+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bandejaRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/casos")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminVeLaBandejaYElDetalleCompleto() throws Exception {
        String token = tokenDe("admin@municipalidad.gob.gt");
        mockMvc.perform(get("/api/casos?codigo=Q-2026-00001").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigoSeguimiento").value("Q-2026-00001"))
                .andExpect(jsonPath("$.content[0].agenteNombre").value("Agente Atención"));
        long casoId = casoIdDe(token, "Q-2026-00001");

        mockMvc.perform(get("/api/casos/" + casoId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailCiudadano").value("ciudadano@email.com"))
                .andExpect(jsonPath("$.descripcion").isNotEmpty())
                .andExpect(jsonPath("$.transicionesPermitidas").isArray());
    }

    @Test
    void agenteVeCasosAsignadosYNuevosDeSuArea() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        mockMvc.perform(get("/api/casos?codigo=Q-2026-00001").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].codigoSeguimiento").value("Q-2026-00001"));
    }

    @Test
    void agenteVeCasosNuevosDeSuAreaIncluyendoAnonimos() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        String identificado = registrarPublico("ATENCION");
        String anonimo = registrarAnonimo();
        String otraArea = registrarPublico("SERVICIOS");

        mockMvc.perform(get("/api/casos?codigo=" + identificado).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].codigoSeguimiento").value(identificado))
                .andExpect(jsonPath("$.content[0].agenteNombre").value("Sin asignar"));

        mockMvc.perform(get("/api/casos?codigo=" + anonimo).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].codigoSeguimiento").value(anonimo))
                .andExpect(jsonPath("$.content[0].ciudadano").value("Anónimo"));

        mockMvc.perform(get("/api/casos?codigo=" + otraArea).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        long casoId = casoIdDe(token, identificado);
        mockMvc.perform(get("/api/casos/" + casoId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento").value(identificado));
    }

    @Test
    void fa01CasoNoDisponible() throws Exception {
        String token = tokenDe("admin@municipalidad.gob.gt");
        mockMvc.perform(get("/api/casos/99999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_DISPONIBLE"));
    }

    @Test
    void fa02AgenteNoPuedeAsignar() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        mockMvc.perform(post("/api/casos/1/asignar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agenteId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
    }

    @Test
    void asignarCasoRecibidoPasaAEnRevision() throws Exception {
        String codigo = registrarPublico("ATENCION");
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoIdDe(admin, codigo);
        long agenteId = agenteId(admin, "ATENCION");

        mockMvc.perform(post("/api/casos/" + casoId + "/asignar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agenteId\":" + agenteId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.caso.agenteAsignadoId").value(agenteId));
    }

    @Test
    void transicionInvalidaNoModificaElCaso() throws Exception {
        String token = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(patch("/api/casos/" + casoId + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"CERRADO\",\"observacion\":\"Intento de cierre directo sin resolución formal.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void supervisorNoPuedeAnular() throws Exception {
        String token = tokenDe("supervisor@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(post("/api/casos/" + casoId + "/anular")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificacion\":\"Anulación de prueba que no debe aplicarse nunca.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
    }

    @Test
    void agenteCambiaEstadoYRegistraObservacion() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(post("/api/casos/" + casoId + "/observaciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Se coordinó inspección de ruta con el área operativa.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.observaciones[0].texto").isNotEmpty());
    }

    private long casoIdDe(String token, String codigo) throws Exception {
        MvcResult lista = mockMvc.perform(get("/api/casos?codigo=" + codigo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(lista.getResponse().getContentAsString()).get("content");
        return content.get(0).get("id").asLong();
    }

    private long agenteId(String token, String area) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/agentes?area=" + area)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("id").asLong();
    }

    private String registrarPublico(String area) throws Exception {
        MvcResult captchaRes = mockMvc.perform(get("/api/casos/captcha")).andReturn();
        JsonNode captcha = objectMapper.readTree(captchaRes.getResponse().getContentAsString());
        Matcher matcher = SUMA.matcher(captcha.get("pregunta").asText());
        matcher.find();
        int suma = Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2));
        String email = "agente-bandeja-" + System.nanoTime() + "@correo.com";
        String body = """
                {
                  "tipoCaso":"S",
                  "nombreCiudadano":"Mario López",
                  "email":"%s",
                  "areaDependencia":"%s",
                  "descripcion":"Solicito seguimiento municipal a una sugerencia de mejora en ventanilla de atención ciudadana %s.",
                  "aceptaPrivacidad":true,
                  "captchaId":"%s",
                  "captchaRespuesta":"%s"
                }
                """.formatted(email, area, email, captcha.get("captchaId").asText(), suma);
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(creado.getResponse().getContentAsString()).get("codigoSeguimiento").asText();
    }

    private String registrarAnonimo() throws Exception {
        MvcResult captchaRes = mockMvc.perform(get("/api/casos/captcha")).andReturn();
        JsonNode captcha = objectMapper.readTree(captchaRes.getResponse().getContentAsString());
        Matcher matcher = SUMA.matcher(captcha.get("pregunta").asText());
        matcher.find();
        int suma = Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2));
        String marca = String.valueOf(System.nanoTime());
        String body = "{"
                + "\"tipoCaso\":\"Q\","
                + "\"descripcion\":\"Queja anonima de prueba para bandeja de agente " + marca + ".\","
                + "\"esAnonimo\":true,"
                + "\"aceptaPrivacidad\":true,"
                + "\"captchaId\":\"" + captcha.get("captchaId").asText() + "\","
                + "\"captchaRespuesta\":\"" + suma + "\""
                + "}";
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(creado.getResponse().getContentAsString()).get("codigoSeguimiento").asText();
    }

    private String tokenDe(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataInitializer.PASSWORD_DEMO + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
