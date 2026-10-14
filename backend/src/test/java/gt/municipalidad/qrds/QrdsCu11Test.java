package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.BitacoraCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import gt.municipalidad.qrds.repository.BitacoraSistemaRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class QrdsCu11Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CasoRepository casoRepository;

    @Autowired
    private BitacoraCasoRepository bitacoraCasoRepository;

    @Autowired
    private BitacoraSistemaRepository bitacoraSistemaRepository;

    @Test
    void adminConsultaEventosDeAccesoYDeCasoSinModificarlos() throws Exception {
        String secreto = "password: SecretoVisible";
        var caso = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow();
        var agente = usuarioRepository.findByEmailIgnoreCase("agente@municipalidad.gob.gt").orElseThrow();
        BitacoraCaso evento = bitacoraCasoRepository.save(
                new BitacoraCaso(caso, agente, TipoEventoCaso.SEGUIMIENTO, secreto, "10.0.0.8"));
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        String hoy = LocalDate.now(ZoneOffset.UTC).toString();

        MvcResult consulta = mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + admin)
                        .param("desde", hoy)
                        .param("hasta", hoy)
                        .param("caso", "Q-2026-00001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipoEvento").value("SEGUIMIENTO"))
                .andExpect(jsonPath("$.content[0].descripcion").value("password: [omitido]"))
                .andReturn();
        String cuerpo = consulta.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("SecretoVisible"));
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains(DataInitializer.PASSWORD_DEMO));

        BitacoraCaso guardado = bitacoraCasoRepository.findById(evento.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(secreto, guardado.getDescripcion());

        mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + admin)
                        .param("desde", hoy)
                        .param("hasta", hoy)
                        .param("dominio", "ACCESO")
                        .param("tipo", "LOGIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipoEvento").value("LOGIN"));
    }

    @Test
    void rangoExcesivoYConsultaVacia() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + admin)
                        .param("desde", "2026-01-01")
                        .param("hasta", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FILTROS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("Reduzca el rango de fechas."));

        long antes = bitacoraSistemaRepository.findByTipoEvento("BITACORA_CONSULTADA").size();
        mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + admin)
                        .param("desde", "1990-01-01")
                        .param("hasta", "1990-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("No hay eventos con los criterios seleccionados."))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + admin)
                        .param("desde", "1990-01-01")
                        .param("hasta", "1990-01-02"))
                .andExpect(status().isOk());
        long despues = bitacoraSistemaRepository.findByTipoEvento("BITACORA_CONSULTADA").size();
        org.junit.jupiter.api.Assertions.assertEquals(antes + 2, despues);
    }

    @Test
    void agenteNoConsultaYLaBitacoraNoSeEdita() throws Exception {
        String agente = tokenDe("agente@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        String hoy = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + agente)
                        .param("desde", hoy)
                        .param("hasta", hoy))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));

        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        mockMvc.perform(delete("/api/bitacoras/CASO/1")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"))
                .andExpect(jsonPath("$.mensaje").value("Los registros de bitácora son de solo lectura."));
        mockMvc.perform(put("/api/bitacoras")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void supervisorSoloVeSuAreaYElIntentoAjenoQuedaRegistrado() throws Exception {
        String supervisor = tokenDe("supervisor@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        long adminId = usuarioRepository.findByEmailIgnoreCase("admin@municipalidad.gob.gt").orElseThrow().getId();
        String hoy = LocalDate.now(ZoneOffset.UTC).toString();
        long denegados = bitacoraSistemaRepository.findByTipoEvento("INTENTO_DENEGADO").size();

        mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + supervisor)
                        .param("desde", hoy)
                        .param("hasta", hoy)
                        .param("usuarioId", String.valueOf(adminId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"))
                .andExpect(jsonPath("$.mensaje").value("No puede consultar eventos fuera de su área."));
        org.junit.jupiter.api.Assertions.assertEquals(
                denegados + 1,
                bitacoraSistemaRepository.findByTipoEvento("INTENTO_DENEGADO").size());

        MvcResult consulta = mockMvc.perform(get("/api/bitacoras")
                        .header("Authorization", "Bearer " + supervisor)
                        .param("desde", hoy)
                        .param("hasta", hoy)
                        .param("dominio", "ACCESO"))
                .andExpect(status().isOk())
                .andReturn();
        String cuerpo = consulta.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("Administrador QRDS"));
        org.junit.jupiter.api.Assertions.assertTrue(cuerpo.contains("Supervisor Atención"));
    }

    @Test
    void exportacionEntregaPdfYRegistraUnSoloEvento() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        String hoy = LocalDate.now(ZoneOffset.UTC).toString();
        long antes = bitacoraSistemaRepository.findByTipoEvento("BITACORA_EXPORTADA").size();
        long consultas = bitacoraSistemaRepository.findByTipoEvento("BITACORA_CONSULTADA").size();

        MvcResult archivo = mockMvc.perform(post("/api/bitacoras/exportar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filtros":{"desde":"%s","hasta":"%s","dominio":"ACCESO"},"formato":"PDF"}
                                """.formatted(hoy, hoy)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andReturn();
        String cuerpo = archivo.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(cuerpo.startsWith("%PDF"));
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains(DataInitializer.PASSWORD_DEMO));
        org.junit.jupiter.api.Assertions.assertEquals(
                antes + 1,
                bitacoraSistemaRepository.findByTipoEvento("BITACORA_EXPORTADA").size());
        org.junit.jupiter.api.Assertions.assertEquals(
                consultas,
                bitacoraSistemaRepository.findByTipoEvento("BITACORA_CONSULTADA").size());
        var exportadas = bitacoraSistemaRepository.findByTipoEvento("BITACORA_EXPORTADA");
        org.junit.jupiter.api.Assertions.assertTrue(exportadas.stream().allMatch(evento ->
                "AUDITORIA".equals(evento.getDominio())
                        && evento.getMetadataJson() != null
                        && !evento.getMetadataJson().contains(DataInitializer.PASSWORD_DEMO)));
    }

    private String tokenDe(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
