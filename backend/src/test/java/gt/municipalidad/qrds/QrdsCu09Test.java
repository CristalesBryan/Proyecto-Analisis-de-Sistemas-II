package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.repository.BitacoraSistemaRepository;
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
class QrdsCu09Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Autowired
    private BitacoraSistemaRepository bitacoraSistemaRepository;

    @Test
    void adminGeneraExcelYDescargaSinModificarElCaso() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        EstadoCaso antes = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow().getEstado();

        MvcResult generado = mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RPT-01", "XLSX", "2026-08-01", "2026-08-31", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.cantidadFilas").value(1))
                .andExpect(jsonPath("$.descargable").value(true))
                .andExpect(jsonPath("$.filas[0][5]").value("Ciudadano Demo"))
                .andReturn();
        long id = objectMapper.readTree(generado.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/reportes/jobs/" + id + "/descarga")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")));

        org.junit.jupiter.api.Assertions.assertEquals(
                antes,
                casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow().getEstado());
        org.junit.jupiter.api.Assertions.assertFalse(
                bitacoraSistemaRepository.findByTipoEventoAndEntidadId("REPORTE_GENERADO", id).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(
                bitacoraSistemaRepository.findByTipoEventoAndEntidadId("REPORTE_DESCARGADO", id).isEmpty());
    }

    @Test
    void supervisorSoloConsultaSuAreaYSeRechazaOtra() throws Exception {
        String supervisor = tokenDe("supervisor@municipalidad.gob.gt");
        mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RPT-01", "PANTALLA", "2026-08-01", "2026-08-31", "OTRA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"))
                .andExpect(jsonPath("$.mensaje").value("No puede consultar información de otra área."));
        org.junit.jupiter.api.Assertions.assertFalse(
                bitacoraSistemaRepository.findByTipoEvento("INTENTO_DENEGADO").isEmpty());

        mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RPT-04", "PANTALLA", "2026-08-01", "2026-08-31", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0][0]").value("Atención al Ciudadano"));
    }

    @Test
    void agenteRecibeProhibido() throws Exception {
        String agente = tokenDe("agente@municipalidad.gob.gt");
        mockMvc.perform(get("/api/reportes/catalogo").header("Authorization", "Bearer " + agente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
        mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + agente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RPT-01", "PDF", "2026-01-01", "2026-01-31", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rangoMayorA365DiasSeRechazaYElVacioNoEsError() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RPT-01", "PANTALLA", "2024-01-01", "2026-01-02", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FILTROS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("El rango de fechas no puede superar 365 días."));

        mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RPT-01", "PANTALLA", "1990-01-01", "1990-01-31", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadFilas").value(0))
                .andExpect(jsonPath("$.mensaje").value("No hay casos con los criterios seleccionados."));
    }

    @Test
    void reutilizaReporteListoYRegistraNuevaDescarga() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        String cuerpo = cuerpo("RPT-03", "PDF", "2026-08-01", "2026-08-31", null);
        MvcResult primero = mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(primero.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/reportes/generar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.reutilizado").value(true));

        mockMvc.perform(get("/api/reportes/jobs/" + id + "/descarga")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
        mockMvc.perform(get("/api/reportes/jobs/" + id + "/descarga")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(
                2, bitacoraSistemaRepository.findByTipoEventoAndEntidadId("REPORTE_DESCARGADO", id).size());
    }

    private String cuerpo(String tipo, String formato, String desde, String hasta, String area) {
        String areaJson = area == null ? "null" : "\"" + area + "\"";
        return """
                {
                  "tipo":"%s",
                  "formato":"%s",
                  "filtros":{
                    "fechaDesde":"%s",
                    "fechaHasta":"%s",
                    "area":%s,
                    "incluirAnulados":false
                  }
                }
                """.formatted(tipo, formato, desde, hasta, areaJson);
    }

    private String tokenDe(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataInitializer.PASSWORD_DEMO + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }
}
