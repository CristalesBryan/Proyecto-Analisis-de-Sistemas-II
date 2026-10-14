package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "qrds.reportes.umbral-filas=0")
class QrdsCu09AsyncTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void volumenAltoQuedaEnColaYLuegoSeDescarga() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        MvcResult generado = mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipo":"RPT-01",
                                  "formato":"XLSX",
                                  "filtros":{
                                    "fechaDesde":"2026-08-01",
                                    "fechaHasta":"2026-08-31",
                                    "incluirAnulados":false
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.asincrono").value(true))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn();
        long id = objectMapper.readTree(generado.getResponse().getContentAsString()).get("id").asLong();

        String estado = "PENDIENTE";
        for (int intento = 0; intento < 40 && ("PENDIENTE".equals(estado) || "PROCESANDO".equals(estado)); intento++) {
            Thread.sleep(100);
            MvcResult job = mockMvc.perform(get("/api/reportes/jobs/" + id)
                            .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk())
                    .andReturn();
            estado = objectMapper.readTree(job.getResponse().getContentAsString()).get("estado").asText();
        }
        org.junit.jupiter.api.Assertions.assertEquals("LISTO", estado);
        mockMvc.perform(get("/api/reportes/jobs/" + id + "/descarga")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
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
