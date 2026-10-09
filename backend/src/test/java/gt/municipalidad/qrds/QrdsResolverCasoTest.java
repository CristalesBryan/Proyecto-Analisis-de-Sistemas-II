package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrdsResolverCasoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void resuelveCasoEnProcesoYNotifica() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoEnProceso(admin, "resolver-ok@correo.com");
        MockMultipartFile constancia = new MockMultipartFile(
                "archivo", "constancia.pdf", "application/pdf", "%PDF-1.4 constancia".getBytes());

        mockMvc.perform(multipart("/api/casos/" + casoId + "/resolver")
                        .file(constancia)
                        .param("comentario", "Se atendió la solicitud y se deja constancia de la respuesta municipal.")
                        .param("tipoResultado", "ATENDIDO")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("El caso fue resuelto y se notificó al ciudadano."))
                .andExpect(jsonPath("$.caso.estado").value("RESUELTO"));
    }

    @Test
    void casoFueraDeProcesoNoCambia() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoRecibido(admin, "resolver-estado@correo.com");

        mockMvc.perform(multipart("/api/casos/" + casoId + "/resolver")
                        .param("comentario", "Intento de resolución sobre un caso recibido.")
                        .param("tipoResultado", "ATENDIDO")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_APTO_RESOLUCION"))
                .andExpect(jsonPath("$.mensaje").value("El caso no se encuentra en estado en proceso."));

        mockMvc.perform(get("/api/casos/" + casoId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECIBIDO"));
    }

    @Test
    void usuarioSinPermisoNoResuelve() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        String agente = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoEnProceso(admin, "resolver-permiso@correo.com", "OBRAS");

        mockMvc.perform(multipart("/api/casos/" + casoId + "/resolver")
                        .param("comentario", "El agente de otra área intenta resolver.")
                        .param("tipoResultado", "ATENDIDO")
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("El usuario no cuenta con permisos para resolver el caso."));

        mockMvc.perform(get("/api/casos/" + casoId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"));
    }

    @Test
    void datosObligatoriosIncompletosNoResuelven() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoEnProceso(admin, "resolver-vacio@correo.com");

        mockMvc.perform(multipart("/api/casos/" + casoId + "/resolver")
                        .param("comentario", " ")
                        .param("tipoResultado", "")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        mockMvc.perform(get("/api/casos/" + casoId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"));
    }

    private long casoEnProceso(String adminToken, String email) throws Exception {
        return casoEnProceso(adminToken, email, "ATENCION");
    }

    private long casoEnProceso(String adminToken, String email, String area) throws Exception {
        long casoId = casoRecibido(adminToken, email, area);
        if ("ATENCION".equals(area)) {
            long agenteId = agenteId(adminToken, area);
            mockMvc.perform(post("/api/casos/" + casoId + "/asignar")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agenteId\":" + agenteId + "}"))
                    .andExpect(status().isOk());
        } else {
            mockMvc.perform(patch("/api/casos/" + casoId + "/estado")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nuevoEstado\":\"EN_REVISION\",\"observacion\":\"El caso pasa a revisión del área correspondiente.\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(patch("/api/casos/" + casoId + "/estado")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_PROCESO\",\"observacion\":\"Se inicia la atención formal del expediente municipal.\"}"))
                .andExpect(status().isOk());
        return casoId;
    }

    private long casoRecibido(String adminToken, String email) throws Exception {
        return casoRecibido(adminToken, email, "ATENCION");
    }

    private long casoRecibido(String adminToken, String email, String area) throws Exception {
        String body = """
                {
                  "tipoCaso":"S",
                  "nombreCiudadano":"Ana Resolución",
                  "email":"%s",
                  "areaDependencia":"%s",
                  "descripcion":"Solicito la resolución formal de una sugerencia municipal ya revisada en ventanilla.",
                  "aceptaPrivacidad":true,
                  "recaptchaToken":"test-token"
                }
                """.formatted(email, area);
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String codigo = objectMapper.readTree(creado.getResponse().getContentAsString()).get("codigoSeguimiento").asText();
        MvcResult lista = mockMvc.perform(get("/api/casos?codigo=" + codigo)
                        .header("Authorization", "Bearer " + adminToken))
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

    private String tokenDe(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataInitializer.PASSWORD_DEMO + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
