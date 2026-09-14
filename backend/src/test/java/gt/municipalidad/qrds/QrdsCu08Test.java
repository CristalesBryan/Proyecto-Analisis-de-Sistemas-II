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
class QrdsCu08Test {

    private static final Pattern SUMA = Pattern.compile("(\\d+) \\+ (\\d+)");
    private static final String OBSERVACION_CIERRE =
            "Se archiva el expediente resuelto tras verificación de la atención municipal.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void cierraCasoResueltoYConservaHistorial() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoResuelto(admin, "cierre-ok@correo.com");

        mockMvc.perform(post("/api/casos/" + casoId + "/cerrar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"" + OBSERVACION_CIERRE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.estado").value("CERRADO"))
                .andExpect(jsonPath("$.caso.historial[0].tipoEvento").value("CIERRE"))
                .andExpect(jsonPath("$.caso.historial[0].estadoNuevo").value("CERRADO"))
                .andExpect(jsonPath("$.caso.descripcion").isNotEmpty());

        mockMvc.perform(patch("/api/casos/" + casoId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "telefono":"55550000",
                                  "motivo":"Intento de modificación sobre un caso ya cerrado."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_MODIFICABLE"));
    }

    @Test
    void fa01CasoNoAptoConservaElEstado() throws Exception {
        String token = tokenDe("supervisor@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");

        mockMvc.perform(post("/api/casos/" + casoId + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"" + OBSERVACION_CIERRE + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_APTO_CIERRE"));

        mockMvc.perform(get("/api/casos/" + casoId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"));
    }

    @Test
    void agenteSinPermisoNoCierra() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(post("/api/casos/" + casoId + "/cerrar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"" + OBSERVACION_CIERRE + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
    }

    @Test
    void cierreDirectoPorCambioDeEstadoSigueBloqueado() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoResuelto(admin, "cierre-directo@correo.com");
        mockMvc.perform(patch("/api/casos/" + casoId + "/estado")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"CERRADO\",\"observacion\":\"Intento de cierre por cambio de estado.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void segundoCierreSobreCasoCerradoEsFa01() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoResuelto(admin, "cierre-doble@correo.com");
        mockMvc.perform(post("/api/casos/" + casoId + "/cerrar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"" + OBSERVACION_CIERRE + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/casos/" + casoId + "/cerrar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"" + OBSERVACION_CIERRE + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_APTO_CIERRE"));
    }

    private long casoResuelto(String adminToken, String email) throws Exception {
        String codigo = registrarPublico("ATENCION", email);
        long casoId = casoIdDe(adminToken, codigo);
        long agenteId = agenteId(adminToken, "ATENCION");
        mockMvc.perform(post("/api/casos/" + casoId + "/asignar")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agenteId\":" + agenteId + "}"))
                .andExpect(status().isOk());
        cambiarEstado(adminToken, casoId, "EN_PROCESO", "Se inicia la atención formal del expediente municipal.");
        cambiarEstado(adminToken, casoId, "RESUELTO", "La solicitud fue atendida y queda lista para archivo.");
        return casoId;
    }

    private void cambiarEstado(String token, long casoId, String estado, String observacion) throws Exception {
        mockMvc.perform(patch("/api/casos/" + casoId + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"" + estado + "\",\"observacion\":\"" + observacion + "\"}"))
                .andExpect(status().isOk());
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

    private String registrarPublico(String area, String email) throws Exception {
        MvcResult captchaRes = mockMvc.perform(get("/api/casos/captcha")).andReturn();
        JsonNode captcha = objectMapper.readTree(captchaRes.getResponse().getContentAsString());
        Matcher matcher = SUMA.matcher(captcha.get("pregunta").asText());
        matcher.find();
        int suma = Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2));
        String body = """
                {
                  "tipoCaso":"S",
                  "nombreCiudadano":"Ana Cierre",
                  "email":"%s",
                  "areaDependencia":"%s",
                  "descripcion":"Solicito cierre de un expediente de sugerencia municipal ya atendido por ventanilla.",
                  "aceptaPrivacidad":true,
                  "captchaId":"%s",
                  "captchaRespuesta":"%s"
                }
                """.formatted(email, area, captcha.get("captchaId").asText(), suma);
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
