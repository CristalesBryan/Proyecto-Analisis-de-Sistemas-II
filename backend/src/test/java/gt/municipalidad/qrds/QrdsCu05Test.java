package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.repository.CasoRepository;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
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
class QrdsCu05Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Test
    void ciudadanoConsultaEstadoYAvancesPublicos() throws Exception {
        mockMvc.perform(get("/api/casos/publico/Q-2026-00001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento").value("Q-2026-00001"))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.avancePorcentaje").value(40))
                .andExpect(jsonPath("$.seguimientos.length()").value(1))
                .andExpect(jsonPath("$.seguimientos[0].tipo").value("PUBLICA"))
                .andExpect(jsonPath("$.seguimientos[0].titulo").value("Caso recibido y en proceso"))
                .andExpect(jsonPath("$.emailCiudadano").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.descripcion").doesNotExist());
    }

    @Test
    void fa01CasoNoEncontradoPermiteNuevaConsulta() throws Exception {
        mockMvc.perform(get("/api/casos/publico/Q-2026-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value(
                        "Código no encontrado. Verifique el número e intente nuevamente."));
    }

    @Test
    void fa02IdentificadorInvalidoNoConsulta() throws Exception {
        mockMvc.perform(get("/api/casos/publico/codigo-malo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(
                        "Los datos ingresados no permiten realizar la consulta."));
    }

    @Test
    void consultaPublicaNoAlteraElCaso() throws Exception {
        Caso antes = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow();
        Instant ultima = antes.getFechaUltimaActualizacion();
        int avance = antes.getAvancePorcentaje();

        mockMvc.perform(get("/api/casos/publico/Q-2026-00001")).andExpect(status().isOk());

        Caso despues = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow();
        Assertions.assertEquals(ultima, despues.getFechaUltimaActualizacion());
        Assertions.assertEquals(avance, despues.getAvancePorcentaje());
        Assertions.assertEquals("EN_PROCESO", despues.getEstado().name());
    }

    @Test
    void usuarioInternoVeNotasInternas() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow().getId();

        mockMvc.perform(get("/api/casos/" + casoId + "/seguimientos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento").value("Q-2026-00001"))
                .andExpect(jsonPath("$.seguimientos.length()").value(2))
                .andExpect(jsonPath("$.seguimientos[*].tipo").value(
                        org.hamcrest.Matchers.hasItems("PUBLICA", "INTERNA")));
    }

    @Test
    void usuarioInternoPuedeOcultarNotasInternas() throws Exception {
        String token = tokenDe("supervisor@municipalidad.gob.gt");
        long casoId = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow().getId();

        mockMvc.perform(get("/api/casos/" + casoId + "/seguimientos?incluirInternos=false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seguimientos.length()").value(1))
                .andExpect(jsonPath("$.seguimientos[0].tipo").value("PUBLICA"));
    }

    @Test
    void consultaInternaRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/casos/1/seguimientos"))
                .andExpect(status().isUnauthorized());
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
