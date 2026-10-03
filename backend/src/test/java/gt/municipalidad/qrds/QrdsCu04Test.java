package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class QrdsCu04Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CasoRepository casoRepository;

    @Test
    void usuarioInternoCargaYDescargaDocumento() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");
        long casoId = casoIdDeCodigo("Q-2026-00001");
        MockMultipartFile pdf = pdf("acta.pdf");

        MvcResult carga = mockMvc.perform(multipart("/api/casos/" + casoId + "/documentos")
                        .file(pdf)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosSubidos[0].nombreArchivo").value("acta.pdf"))
                .andExpect(jsonPath("$.archivosSubidos[0].id").isNumber())
                .andReturn();
        long docId = objectMapper.readTree(carga.getResponse().getContentAsString())
                .get("archivosSubidos").get(0).get("id").asLong();

        mockMvc.perform(get("/api/casos/" + casoId + "/documentos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreArchivo").value("acta.pdf"));

        mockMvc.perform(get("/api/casos/" + casoId + "/documentos/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("acta.pdf")));
    }

    @Test
    void cargaInternaRequiereAutenticacion() throws Exception {
        mockMvc.perform(multipart("/api/casos/1/documentos").file(pdf("acta.pdf")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaArchivoConExtensionInvalidaOContenidoFalso() throws Exception {
        String token = tokenDe("supervisor@municipalidad.gob.gt");
        long casoId = casoIdDeCodigo("Q-2026-00001");
        MockMultipartFile exe = new MockMultipartFile(
                "archivos", "virus.exe", "application/octet-stream", "MZ".getBytes());
        MockMultipartFile falsoPdf = new MockMultipartFile(
                "archivos", "falso.pdf", "application/pdf", "esto no es un pdf".getBytes());

        mockMvc.perform(multipart("/api/casos/" + casoId + "/documentos")
                        .file(exe)
                        .file(falsoPdf)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosSubidos.length()").value(0))
                .andExpect(jsonPath("$.rechazados.length()").value(2));
    }

    @Test
    void noPermiteCargarEnCasoCerrado() throws Exception {
        String codigo = registrarCasoPublico();
        Caso caso = casoRepository.findByCodigoSeguimientoIgnoreCase(codigo).orElseThrow();
        caso.setEstado(EstadoCaso.CERRADO);
        casoRepository.save(caso);

        String token = tokenDe("admin@municipalidad.gob.gt");
        mockMvc.perform(multipart("/api/casos/" + caso.getId() + "/documentos")
                        .file(pdf("cierre.pdf"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_HABILITADO"));
    }

    private MockMultipartFile pdf(String nombre) {
        return new MockMultipartFile("archivos", nombre, "application/pdf", "%PDF-1.4 evidencia".getBytes());
    }

    private long casoIdDeCodigo(String codigo) {
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

    private String registrarCasoPublico() throws Exception {
        String body = """
                {
                  "tipoCaso":"S",
                  "nombreCiudadano":"Eva Soto",
                  "email":"eva-docs@correo.com",
                  "areaDependencia":"SERVICIOS",
                  "descripcion":"Adjunto evidencia adicional para una sugerencia de mejora en ventanilla única.",
                  "aceptaPrivacidad":true,
                  "recaptchaToken":"test-token"
                }
                """;
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(creado.getResponse().getContentAsString()).get("codigoSeguimiento").asText();
    }
}
