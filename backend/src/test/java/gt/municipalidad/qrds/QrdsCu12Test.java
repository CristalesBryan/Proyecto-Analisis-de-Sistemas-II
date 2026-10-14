package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.DocumentoCasoRepository;
import java.nio.file.Files;
import java.nio.file.Path;
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
class QrdsCu12Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Autowired
    private DocumentoCasoRepository documentoCasoRepository;

    @Autowired
    private BitacoraCasoRepository bitacoraCasoRepository;

    @Test
    void ciudadanoAdjuntaPdfYElNombreInternoNoEsElOriginal() throws Exception {
        String codigo = registrarCasoPublico("Lucia Campos", "lucia-cu12@correo.com");
        MockMultipartFile pdf = pdf("evidencia.pdf");

        MvcResult carga = mockMvc.perform(multipart("/api/casos/publico/" + codigo + "/documentos").file(pdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosSubidos[0].nombreArchivo").value("evidencia.pdf"))
                .andReturn();
        long docId = objectMapper.readTree(carga.getResponse().getContentAsString())
                .get("archivosSubidos").get(0).get("id").asLong();
        String ruta = documentoCasoRepository.findById(docId).orElseThrow().getRutaArchivo();
        org.junit.jupiter.api.Assertions.assertTrue(ruta.endsWith(".pdf"));
        org.junit.jupiter.api.Assertions.assertFalse(ruta.contains("evidencia.pdf"));

        mockMvc.perform(get("/api/casos/publico/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos").doesNotExist())
                .andExpect(jsonPath("$.rutaArchivo").doesNotExist());
    }

    @Test
    void rechazaEjecutableYArchivoMayorAlLimiteSinAsociarlos() throws Exception {
        String codigo = registrarCasoPublico("Mario Campos", "mario-cu12@correo.com");
        MockMultipartFile exe = new MockMultipartFile(
                "archivos", "virus.exe", "application/octet-stream", "MZ".getBytes());
        byte[] grande = new byte[(int) (5L * 1024 * 1024) + 20];
        grande[0] = '%';
        grande[1] = 'P';
        grande[2] = 'D';
        grande[3] = 'F';
        MockMultipartFile pesado = new MockMultipartFile("archivos", "grande.pdf", "application/pdf", grande);

        mockMvc.perform(multipart("/api/casos/publico/" + codigo + "/documentos").file(exe).file(pesado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosSubidos.length()").value(0))
                .andExpect(jsonPath("$.rechazados[0].motivo").exists())
                .andExpect(jsonPath("$.rechazados[1].motivo").exists());
    }

    @Test
    void agenteDescargaSuCasoYNoElDeOtraArea() throws Exception {
        String agente = tokenDe("agente@municipalidad.gob.gt");
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long propio = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow().getId();

        MvcResult carga = mockMvc.perform(multipart("/api/casos/" + propio + "/documentos")
                        .file(pdf("acta-propia.pdf"))
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isOk())
                .andReturn();
        long docId = objectMapper.readTree(carga.getResponse().getContentAsString())
                .get("archivosSubidos").get(0).get("id").asLong();

        mockMvc.perform(get("/api/casos/" + propio + "/documentos/" + docId)
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("acta-propia.pdf")));
        org.junit.jupiter.api.Assertions.assertTrue(bitacoraCasoRepository.findByCasoOrderByFechaHoraDesc(
                        casoRepository.findById(propio).orElseThrow()).stream()
                .anyMatch(evento -> evento.getTipoEvento() == TipoEventoCaso.DOCUMENTO_DESCARGADO));

        String codigoAjeno = registrarCasoPublico("Nora Ajena", "nora-cu12@correo.com");
        Caso ajeno = casoRepository.findByCodigoSeguimientoIgnoreCase(codigoAjeno).orElseThrow();
        MvcResult cargaAjena = mockMvc.perform(multipart("/api/casos/" + ajeno.getId() + "/documentos")
                        .file(pdf("ajeno.pdf"))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        long docAjeno = objectMapper.readTree(cargaAjena.getResponse().getContentAsString())
                .get("archivosSubidos").get(0).get("id").asLong();

        mockMvc.perform(get("/api/casos/" + ajeno.getId() + "/documentos/" + docAjeno)
                        .header("Authorization", "Bearer " + agente))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertTrue(bitacoraCasoRepository.findByCasoOrderByFechaHoraDesc(ajeno).stream()
                .anyMatch(evento -> evento.getTipoEvento() == TipoEventoCaso.INTENTO_DESCARGA_DENEGADO));
    }

    @Test
    void casoResueltoAdmiteCargaYElCerradoSoloConExcepcion() throws Exception {
        String codigo = registrarCasoPublico("Elena Ruiz", "elena-cu12@correo.com");
        Caso caso = casoRepository.findByCodigoSeguimientoIgnoreCase(codigo).orElseThrow();
        String admin = tokenDe("admin@municipalidad.gob.gt");

        caso.setEstado(EstadoCaso.RESUELTO);
        casoRepository.save(caso);
        mockMvc.perform(multipart("/api/casos/" + caso.getId() + "/documentos")
                        .file(pdf("resuelto.pdf"))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosSubidos[0].nombreArchivo").value("resuelto.pdf"));

        caso.setEstado(EstadoCaso.CERRADO);
        casoRepository.save(caso);
        mockMvc.perform(get("/api/casos/" + caso.getId() + "/documentos")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreArchivo").value("resuelto.pdf"))
                .andExpect(jsonPath("$[0].rutaArchivo").doesNotExist());
        mockMvc.perform(multipart("/api/casos/" + caso.getId() + "/documentos")
                        .file(pdf("sin-motivo.pdf"))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_HABILITADO"));
        mockMvc.perform(multipart("/api/casos/" + caso.getId() + "/documentos")
                        .file(pdf("con-motivo.pdf"))
                        .param("justificacion", "Autorizo la carga excepcional del expediente cerrado.")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosSubidos[0].nombreArchivo").value("con-motivo.pdf"));
    }

    @Test
    void bajaConservaElArchivoYElFaltanteAvisaSinAlterarElCaso() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt");
        long casoId = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").orElseThrow().getId();
        MvcResult carga = mockMvc.perform(multipart("/api/casos/" + casoId + "/documentos")
                        .file(pdf("conservar.pdf"))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        long docId = objectMapper.readTree(carga.getResponse().getContentAsString())
                .get("archivosSubidos").get(0).get("id").asLong();
        var documento = documentoCasoRepository.findById(docId).orElseThrow();
        Path ruta = Path.of(documento.getRutaArchivo());
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(ruta));

        mockMvc.perform(delete("/api/casos/" + casoId + "/documentos/" + docId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificacion\":\"Retiro el adjunto duplicado del expediente.\"}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(ruta));
        org.junit.jupiter.api.Assertions.assertFalse(documentoCasoRepository.findById(docId).orElseThrow().isActivo());

        String codigo = registrarCasoPublico("Sara Faltante", "sara-cu12@correo.com");
        Caso caso = casoRepository.findByCodigoSeguimientoIgnoreCase(codigo).orElseThrow();
        MvcResult otro = mockMvc.perform(multipart("/api/casos/" + caso.getId() + "/documentos")
                        .file(pdf("perdido.pdf"))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        long perdido = objectMapper.readTree(otro.getResponse().getContentAsString())
                .get("archivosSubidos").get(0).get("id").asLong();
        Files.delete(Path.of(documentoCasoRepository.findById(perdido).orElseThrow().getRutaArchivo()));
        EstadoCaso antes = caso.getEstado();

        mockMvc.perform(get("/api/casos/" + caso.getId() + "/documentos/" + perdido)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_NO_ENCONTRADO"));
        org.junit.jupiter.api.Assertions.assertEquals(antes, casoRepository.findById(caso.getId()).orElseThrow().getEstado());
        org.junit.jupiter.api.Assertions.assertTrue(bitacoraCasoRepository.findByCasoOrderByFechaHoraDesc(caso).stream()
                .anyMatch(evento -> evento.getTipoEvento() == TipoEventoCaso.DOCUMENTO_FALTANTE));
    }

    private MockMultipartFile pdf(String nombre) {
        return new MockMultipartFile("archivos", nombre, "application/pdf", "%PDF-1.4 evidencia".getBytes());
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
                  "descripcion":"Adjunto evidencia de una sugerencia sobre el mantenimiento de la banqueta municipal.",
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
