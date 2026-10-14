package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
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
class QrdsCu13Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Autowired
    private BitacoraCasoRepository bitacoraCasoRepository;

    @Test
    void agenteVeRegistroSeguimientosYNoCambiaElCaso() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = idDe("Q-2026-00001");
        EstadoCaso antes = casoRepository.findById(casoId).orElseThrow().getEstado();

        mockMvc.perform(get("/api/casos/" + casoId + "/historial")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[?(@.tipo == 'CREACION')]").exists())
                .andExpect(jsonPath("$.eventos[?(@.tipo == 'SEGUIMIENTO_PUBLICO')].interno", Matchers.contains(false)))
                .andExpect(jsonPath("$.eventos[?(@.tipo == 'SEGUIMIENTO_INTERNO')].resumen", Matchers.contains("Nota interna de supervisión")))
                .andExpect(jsonPath("$.eventos[?(@.tipo == 'SEGUIMIENTO_INTERNO')].interno", Matchers.contains(true)))
                .andExpect(jsonPath("$.eventos[?(@.tipo == 'ASIGNACION')].resumen", Matchers.contains("Asignado a Agente Atención.")))
                .andExpect(jsonPath("$..rutaArchivo").doesNotExist());

        org.junit.jupiter.api.Assertions.assertEquals(antes, casoRepository.findById(casoId).orElseThrow().getEstado());
    }

    @Test
    void filtroYBusquedaDejanSoloElEventoPedido() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = idDe("Q-2026-00001");

        mockMvc.perform(get("/api/casos/" + casoId + "/historial")
                        .header("Authorization", "Bearer " + token)
                        .param("categoria", "SEGUIMIENTOS")
                        .param("q", "Nota interna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.eventos[0].categoria").value("SEGUIMIENTOS"))
                .andExpect(jsonPath("$.eventos[0].interno").value(true));
    }

    @Test
    void consultaPublicaNoExponeElHistorialInterno() throws Exception {
        mockMvc.perform(get("/api/casos/publico/Q-2026-00001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial").doesNotExist())
                .andExpect(jsonPath("$.seguimientos[?(@.titulo == 'Nota interna de supervisión')]").isEmpty())
                .andExpect(jsonPath("$.emailCiudadano").doesNotExist());
    }

    @Test
    void agenteNoConsultaUnCasoAjenoNiLaVistaCiudadana() throws Exception {
        String codigo = registrarCasoPublico("Elena Ruiz", "elena-cu13@correo.com");
        long ajeno = idDe(codigo);
        String agente = tokenDe("agente@municipalidad.gob.gt");

        mockMvc.perform(get("/api/casos/" + ajeno + "/historial")
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));

        long propio = idDe("Q-2026-00001");
        mockMvc.perform(get("/api/casos/" + propio + "/historial/vista-ciudadano")
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
    }

    @Test
    void supervisorVeLaVistaPublicaSinNotasInternas() throws Exception {
        String token = tokenDe("supervisor@municipalidad.gob.gt");
        long casoId = idDe("Q-2026-00001");

        mockMvc.perform(get("/api/casos/" + casoId + "/historial/vista-ciudadano")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.seguimientos[?(@.titulo == 'Caso recibido y en proceso')]").exists())
                .andExpect(jsonPath("$.seguimientos[?(@.titulo == 'Nota interna de supervisión')]").isEmpty())
                .andExpect(jsonPath("$.emailCiudadano").doesNotExist())
                .andExpect(jsonPath("$.observaciones").doesNotExist());
    }

    @Test
    void exportacionQuedaMarcadaYRegistradaSinAlterarElCaso() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        Caso caso = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow();
        EstadoCaso antes = caso.getEstado();

        mockMvc.perform(get("/api/casos/" + caso.getId() + "/historial/export.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(result -> {
                    String texto = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1);
                    org.junit.jupiter.api.Assertions.assertTrue(texto.contains("Uso interno"));
                    org.junit.jupiter.api.Assertions.assertFalse(texto.contains("ruta_archivo"));
                });

        org.junit.jupiter.api.Assertions.assertEquals(antes, casoRepository.findById(caso.getId()).orElseThrow().getEstado());
        org.junit.jupiter.api.Assertions.assertTrue(
                bitacoraCasoRepository.findByCasoOrderByFechaHoraDesc(caso).stream()
                        .anyMatch(item -> item.getTipoEvento() == TipoEventoCaso.HISTORIAL_EXPORTADO));
    }

    private long idDe(String codigo) {
        return casoRepository.findByCodigoSeguimientoIgnoreCase(codigo).orElseThrow().getId();
    }

    private String tokenDe(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataInitializer.PASSWORD_DEMO + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String registrarCasoPublico(String nombre, String email) throws Exception {
        String body = """
                {
                  "tipoCaso":"S",
                  "nombreCiudadano":"%s",
                  "email":"%s",
                  "areaDependencia":"OBRAS",
                  "descripcion":"Solicito revisar el historial de una sugerencia sobre el alumbrado de la avenida municipal.",
                  "aceptaPrivacidad":true,
                  "recaptchaToken":"test-token"
                }
                """.formatted(nombre, email);
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(creado.getResponse().getContentAsString()).get("codigoSeguimiento").asText();
    }
}
