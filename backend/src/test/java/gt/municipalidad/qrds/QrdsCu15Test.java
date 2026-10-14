package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.BitacoraSistema;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.repository.BitacoraSistemaRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.service.PlazoService;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
class QrdsCu15Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Autowired
    private BitacoraSistemaRepository bitacoraSistemaRepository;

    @Autowired
    private PlazoService plazoService;

    @Test
    void administradorCambiaElPlazoYElCasoNuevoLoUsa() throws Exception {
        String admin = token("admin@municipalidad.gob.gt");
        Caso existente = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow();
        LocalDate limitePrevio = existente.getFechaLimiteRespuesta();
        long version = versionDe(admin, "plazo.Q");

        mockMvc.perform(put("/api/parametros/plazo.Q")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"10\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAnterior").value("15"))
                .andExpect(jsonPath("$.valorNuevo").value("10"));

        org.junit.jupiter.api.Assertions.assertEquals(
                limitePrevio,
                casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow().getFechaLimiteRespuesta());
        org.junit.jupiter.api.Assertions.assertTrue(bitacoraSistemaRepository.findAll().stream()
                .map(BitacoraSistema::getDescripcion)
                .anyMatch(texto -> texto.contains("plazo.Q") && texto.contains("de 15 a 10")));

        String codigo = registrar("Q", "elena-cu15-plazo@correo.com", "ATENCION");
        LocalDate esperado = plazoService.fechaLimite(LocalDate.now(), 10);
        org.junit.jupiter.api.Assertions.assertEquals(
                esperado,
                casoRepository.findByCodigoSeguimientoIgnoreCase(codigo).orElseThrow().getFechaLimiteRespuesta());

        mockMvc.perform(put("/api/parametros/plazo.Q")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"0\",\"version\":" + (version + 1) + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void supervisorConsultaYNoModifica() throws Exception {
        String supervisor = token("supervisor@municipalidad.gob.gt");
        mockMvc.perform(get("/api/parametros").header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeEditar").value(false))
                .andExpect(jsonPath("$.areas").isArray());
        mockMvc.perform(put("/api/parametros/plazo.R")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"12\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parametros").header("Authorization", "Bearer " + token("agente@municipalidad.gob.gt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaCorreoInvalidoAreaRepetidaYConflictoDeVersion() throws Exception {
        String admin = token("admin@municipalidad.gob.gt");
        long version = versionDe(admin, "correo.from");
        mockMvc.perform(put("/api/parametros/correo.from")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"sin-arroba\",\"version\":" + version + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mockMvc.perform(put("/api/parametros/correo.from")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"avisos@municipalidad.gob.gt\",\"version\":999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El valor vigente cambió. Revise el parámetro antes de confirmar de nuevo."));
        mockMvc.perform(post("/api/parametros/areas")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"ATENCION\",\"nombre\":\"Otra atención\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.codigo").value("Ya existe un área con ese código."));
    }

    @Test
    void areaConHistorialNoSeEliminaYElFeriadoNoReescribePlazos() throws Exception {
        String admin = token("admin@municipalidad.gob.gt");
        long atencion = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow()
                .getAreaDependencia().getId();
        mockMvc.perform(delete("/api/parametros/areas/" + atencion).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Un área con historial no se elimina. Puede desactivarla."));

        String primero = registrar("S", "elena-cu15-feriado-1@correo.com", "ATENCION");
        LocalDate limite = casoRepository.findByCodigoSeguimientoIgnoreCase(primero).orElseThrow().getFechaLimiteRespuesta();
        LocalDate feriado = siguienteHabil(LocalDate.now().plusDays(1));
        mockMvc.perform(post("/api/parametros/feriados")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + feriado + "\",\"descripcion\":\"Feriado municipal de prueba\"}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(
                limite,
                casoRepository.findByCodigoSeguimientoIgnoreCase(primero).orElseThrow().getFechaLimiteRespuesta());
        String segundo = registrar("S", "elena-cu15-feriado-2@correo.com", "ATENCION");
        LocalDate limiteNuevo = casoRepository.findByCodigoSeguimientoIgnoreCase(segundo).orElseThrow().getFechaLimiteRespuesta();
        org.junit.jupiter.api.Assertions.assertTrue(limiteNuevo.isAfter(limite));
    }

    private long versionDe(String token, String clave) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/parametros").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode grupos = objectMapper.readTree(result.getResponse().getContentAsString()).get("grupos");
        for (JsonNode grupo : grupos) {
            for (JsonNode item : grupo.get("parametros")) {
                if (clave.equals(item.get("clave").asText())) {
                    return item.get("version").asLong();
                }
            }
        }
        throw new AssertionError(clave);
    }

    private String registrar(String tipo, String email, String area) throws Exception {
        String body = """
                {
                  "tipoCaso":"%s",
                  "nombreCiudadano":"Elena Ruiz",
                  "email":"%s",
                  "areaDependencia":"%s",
                  "descripcion":"Solicito revisar el alumbrado de la avenida municipal porque las luminarias fallan de noche.",
                  "aceptaPrivacidad":true,
                  "recaptchaToken":"test-token"
                }
                """.formatted(tipo, email, area);
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(creado.getResponse().getContentAsString()).get("codigoSeguimiento").asText();
    }

    private String token(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataInitializer.PASSWORD_DEMO + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private LocalDate siguienteHabil(LocalDate fecha) {
        LocalDate dia = fecha;
        while (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }
}
