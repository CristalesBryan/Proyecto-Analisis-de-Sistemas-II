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
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.repository.CasoRepository;
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
class QrdsCu07Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Test
    void modificaDatosPermitidosYDejaBitacora() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(patch("/api/casos/" + casoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "telefono":"55559876",
                                  "prioridad":"ALTA",
                                  "motivo":"Actualización de contacto solicitada por el ciudadano."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.telefono").value("55559876"))
                .andExpect(jsonPath("$.caso.prioridad").value("ALTA"))
                .andExpect(jsonPath("$.caso.historial[0].tipoEvento").value("MODIFICACION"));
    }

    @Test
    void fa01DatosInvalidosConservaElFormulario() throws Exception {
        String token = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(patch("/api/casos/" + casoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "descripcion":"corta",
                                  "motivo":"Corrección de descripción del expediente municipal."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.descripcion").exists());
    }

    @Test
    void fa02CasoCerradoNoEsModificable() throws Exception {
        String token = tokenDe("admin@municipalidad.gob.gt");
        Caso caso = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow();
        EstadoCaso original = caso.getEstado();
        caso.setEstado(EstadoCaso.CERRADO);
        casoRepository.save(caso);
        try {
            mockMvc.perform(patch("/api/casos/" + caso.getId())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "telefono":"11111111",
                                      "motivo":"Intento de cambio sobre un caso ya cerrado."
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CASO_NO_MODIFICABLE"));
        } finally {
            Caso restaurar = casoRepository.findById(caso.getId()).orElseThrow();
            restaurar.setEstado(original);
            casoRepository.save(restaurar);
        }
    }

    @Test
    void registraSeguimientoPublicoYProrroga() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(multipart("/api/casos/" + casoId + "/seguimientos")
                        .file(new MockMultipartFile(
                                "archivo", "acta.pdf", "application/pdf", "%PDF-1.4 evidencia".getBytes()))
                        .param("tipo", "PUBLICA")
                        .param("titulo", "Inspección programada")
                        .param("descripcion", "Se coordinó visita de campo para verificar la ruta de recolección.")
                        .param("porcentajeAvance", "55")
                        .param("notificarCiudadano", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seguimiento.tipo").value("PUBLICA"))
                .andExpect(jsonPath("$.caso.avancePorcentaje").value(55))
                .andExpect(jsonPath("$.seguimiento.adjunto.nombreArchivo").value("acta.pdf"));

        mockMvc.perform(get("/api/casos/publico/Q-2026-00001/seguimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seguimientos[0].titulo").value("Inspección programada"));

        String supervisor = tokenDe("supervisor@municipalidad.gob.gt");
        mockMvc.perform(post("/api/casos/" + casoId + "/prorroga")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diasHabiles":3,
                                  "justificacion":"Se requiere tiempo adicional por inspección de campo pendiente."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.fechaProrroga").isNotEmpty());
    }

    @Test
    void escalaCasoAPrioridadAlta() throws Exception {
        String token = tokenDe("supervisor@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(post("/api/casos/" + casoId + "/escalar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"El plazo está por vencer y requiere atención inmediata.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.prioridad").value("ALTA"))
                .andExpect(jsonPath("$.caso.escalado").value(true));
    }

    @Test
    void agenteNoPuedeEscalar() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoIdDe(token, "Q-2026-00001");
        mockMvc.perform(post("/api/casos/" + casoId + "/escalar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Intento de escalamiento no autorizado por el agente.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
    }

    private long casoIdDe(String token, String codigo) throws Exception {
        MvcResult lista = mockMvc.perform(get("/api/casos?codigo=" + codigo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(lista.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();
    }

    private String tokenDe(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataInitializer.PASSWORD_DEMO + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
