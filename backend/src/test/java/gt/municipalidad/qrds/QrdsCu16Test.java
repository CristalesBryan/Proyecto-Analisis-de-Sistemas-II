package gt.municipalidad.qrds;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.BitacoraSistemaRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
class QrdsCu16Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AreaDependenciaRepository areaRepository;

    @Autowired
    private BitacoraSistemaRepository bitacoraSistemaRepository;

    @Test
    void cadaRolVeSuAlcanceYElTableroNoModificaElCaso() throws Exception {
        Caso obras = guardar("R-2026-16001", "OBRAS", EstadoCaso.RECIBIDO, Instant.now(), LocalDate.now(ZoneOffset.UTC).minusDays(3), null);
        Caso propio = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow();
        EstadoCaso estadoAntes = propio.getEstado();
        String admin = tokenDe("admin@municipalidad.gob.gt");
        String agente = tokenDe("agente@municipalidad.gob.gt");
        String supervisor = tokenDe("supervisor@municipalidad.gob.gt");

        MvcResult vistaAdmin = mockMvc.perform(get("/api/dashboard/resumen")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("30d"))
                .andExpect(jsonPath("$.alcance").value("INSTITUCION"))
                .andExpect(jsonPath("$.puedeReportes").value(true))
                .andExpect(jsonPath("$.cargaAgentes").isArray())
                .andExpect(jsonPath("$.topVencidos[?(@.codigo == 'Q-2026-00001')].codigo", contains("Q-2026-00001")))
                .andReturn();
        String cuerpoAdmin = vistaAdmin.getResponse().getContentAsString();
        assertFalse(cuerpoAdmin.contains("Ciudadano Demo"));
        assertFalse(cuerpoAdmin.contains("ciudadano@email.com"));
        long abiertosAdmin = objectMapper.readTree(cuerpoAdmin).get("abiertos").asLong();

        mockMvc.perform(get("/api/dashboard/resumen")
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance").value("AREA"))
                .andExpect(jsonPath("$.puedeReportes").value(true))
                .andExpect(jsonPath("$.abiertos").value(not(abiertosAdmin)))
                .andExpect(jsonPath("$.topVencidos[?(@.codigo == 'R-2026-16001')]").isEmpty());

        mockMvc.perform(get("/api/dashboard/resumen")
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcance").value("ASIGNADOS"))
                .andExpect(jsonPath("$.puedeReportes").value(false))
                .andExpect(jsonPath("$.recibidos").value(nullValue()))
                .andExpect(jsonPath("$.anulados").value(nullValue()))
                .andExpect(jsonPath("$.cargaAgentes").value(nullValue()))
                .andExpect(jsonPath("$.abiertos").value(greaterThan(0)))
                .andExpect(jsonPath("$.topVencidos[?(@.codigo == 'R-2026-16001')]").isEmpty())
                .andExpect(jsonPath("$.topVencidos[?(@.codigo == 'Q-2026-00001')].codigo", contains("Q-2026-00001")));

        assertEquals(estadoAntes, casoRepository.findById(propio.getId()).orElseThrow().getEstado());
        assertTrue(bitacoraSistemaRepository.findAll().stream()
                .anyMatch(evento -> "DASHBOARD_CONSULTADO".equals(evento.getTipoEvento())
                        && "AUDITORIA".equals(evento.getDominio())));

        mockMvc.perform(get("/api/casos")
                        .param("vencido", "true")
                        .param("codigo", "Q-2026-00001")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigoSeguimiento").value("Q-2026-00001"))
                .andExpect(jsonPath("$.content[0].estado").value(estadoAntes.name()));

        mockMvc.perform(get("/api/casos")
                        .param("vencido", "true")
                        .param("soloAsignados", "true")
                        .param("codigo", obras.getCodigoSeguimiento())
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/dashboard/resumen")
                        .header("Authorization", "Bearer " + tokenDe("ciudadano@email.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elPeriodoDeSieteDiasNoCuentaUnCasoDeHaceVeinteDias() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long antes7 = abiertosRecibidos(admin, "7d");
        long antes30 = abiertosRecibidos(admin, "30d");
        guardar(
                "Q-2026-16020",
                "ATENCION",
                EstadoCaso.RECIBIDO,
                Instant.now().minus(20, ChronoUnit.DAYS),
                LocalDate.now(ZoneOffset.UTC).plusDays(10),
                usuarioRepository.findByEmailIgnoreCase("agente@municipalidad.gob.gt").orElseThrow());

        assertEquals(antes7, abiertosRecibidos(admin, "7d"));
        assertEquals(antes30 + 1, abiertosRecibidos(admin, "30d"));

        mockMvc.perform(get("/api/dashboard/resumen").param("periodo", "ano")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    private long abiertosRecibidos(String token, String periodo) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/dashboard/resumen").param("periodo", periodo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("recibidos").asLong();
    }

    private Caso guardar(
            String codigo,
            String area,
            EstadoCaso estado,
            Instant registro,
            LocalDate limite,
            Usuario agente) {
        Caso caso = new Caso();
        caso.setCodigoSeguimiento(codigo);
        caso.setTipoCaso(TipoCaso.Q);
        caso.setAreaDependencia(areaRepository.findByCodigo(area).orElseThrow());
        caso.setDescripcion("Caso de prueba del tablero de indicadores.");
        caso.setEstado(estado);
        caso.setFechaRegistro(registro);
        caso.setFechaLimiteRespuesta(limite);
        caso.setAgenteAsignado(agente);
        caso.setAceptaPrivacidad(true);
        return casoRepository.save(caso);
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
