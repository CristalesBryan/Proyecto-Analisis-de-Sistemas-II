package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
class QrdsCu03Test {

    private static final Pattern SUMA = Pattern.compile("(\\d+) \\+ (\\d+)");
    private static final String DESCRIPCION =
            "El camión de basura no pasa por la colonia desde hace dos semanas y hay acumulación en la esquina.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void catalogoDeAreasEsPublico() throws Exception {
        mockMvc.perform(get("/api/catalogos/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").isNotEmpty())
                .andExpect(jsonPath("$[0].nombre").isNotEmpty());
    }

    @Test
    void registraQuejaYGeneraCodigoUnico() throws Exception {
        Captcha captcha = captcha();
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Q", "Ana Pérez", "ana@correo.com", DESCRIPCION, false, captcha, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoSeguimiento").value(org.hamcrest.Matchers.matchesPattern("Q-\\d{4}-\\d{5}")))
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.tipo").value("Queja"))
                .andExpect(jsonPath("$.plazoEstimado").value("15 días hábiles"))
                .andExpect(jsonPath("$.correoEnviado").value(true));
    }

    @Test
    void consultaPublicaDelNuevoCasoNoExponeDatosPersonales() throws Exception {
        Captcha captcha = captcha();
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(
                                "R",
                                "Luis Gómez",
                                "luis@correo.com",
                                "Solicito revisión de una multa que considero incorrecta por un cobro duplicado.",
                                false,
                                captcha,
                                false)))
                .andExpect(status().isCreated())
                .andReturn();
        String codigo = objectMapper.readTree(creado.getResponse().getContentAsString())
                .get("codigoSeguimiento")
                .asText();

        mockMvc.perform(get("/api/casos/publico/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento").value(codigo))
                .andExpect(jsonPath("$.emailCiudadano").doesNotExist())
                .andExpect(jsonPath("$.nombreCiudadano").doesNotExist())
                .andExpect(jsonPath("$.descripcion").doesNotExist());
    }

    @Test
    void rechazaRegistroSinPrivacidadNiDescripcionCorta() throws Exception {
        Captcha captcha = captcha();
        String body = """
                {
                  "tipoCaso":"Q",
                  "nombreCiudadano":"Ana",
                  "email":"ana@correo.com",
                  "areaDependencia":"SERVICIOS",
                  "descripcion":"muy corto",
                  "aceptaPrivacidad":false,
                  "captchaId":"%s",
                  "captchaRespuesta":"%s"
                }
                """.formatted(captcha.id(), captcha.respuesta());
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void captchaInvalidoImpideElRegistro() throws Exception {
        Captcha captcha = captcha();
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Q", "Ana Pérez", "ana2@correo.com", DESCRIPCION, false,
                                new Captcha(captcha.id(), "999"), false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CAPTCHA_INVALIDO"));
    }

    @Test
    void quejaAnonimaSoloRequiereDescripcion() throws Exception {
        Captcha captcha = captcha();
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoCaso":"Q",
                                  "descripcion":"El camión de basura no pasa por la colonia desde hace dos semanas y hay acumulación en la esquina.",
                                  "esAnonimo":true,
                                  "aceptaPrivacidad":true,
                                  "captchaId":"%s",
                                  "captchaRespuesta":"%s"
                                }
                                """.formatted(captcha.id(), captcha.respuesta())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esAnonimo").value(true))
                .andExpect(jsonPath("$.correoEnviado").value(false))
                .andExpect(jsonPath("$.codigoSeguimiento").value(org.hamcrest.Matchers.startsWith("Q-")));
    }

    @Test
    void denunciaAnonimaNoRequiereNombreNiCorreo() throws Exception {
        Captcha captcha = captcha();
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(
                                "D",
                                "",
                                "",
                                "Denuncio una irregularidad en la atención de ventanilla el día de ayer por la mañana.",
                                true,
                                captcha,
                                false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esAnonimo").value(true))
                .andExpect(jsonPath("$.correoEnviado").value(false))
                .andExpect(jsonPath("$.codigoSeguimiento").value(org.hamcrest.Matchers.startsWith("D-")));
    }

    @Test
    void detectaCasoSimilarYPermiteForzarRegistro() throws Exception {
        String descripcion = "El alumbrado público de la 4a avenida no funciona desde el lunes por la noche.";
        Captcha primero = captcha();
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Q", "Mario Ruiz", "mario@correo.com", descripcion, false, primero, false)))
                .andExpect(status().isCreated());

        Captcha segundo = captcha();
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Q", "Mario Ruiz", "mario@correo.com", descripcion, false, segundo, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_SIMILAR"))
                .andExpect(jsonPath("$.codigoExistente").isNotEmpty());

        Captcha tercero = captcha();
        mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Q", "Mario Ruiz", "mario@correo.com", descripcion, false, tercero, true)))
                .andExpect(status().isCreated());
    }

    @Test
    void adjuntaDocumentoValidoYRechazaFormatoNoPermitido() throws Exception {
        Captcha captcha = captcha();
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(
                                "S",
                                "Carla Méndez",
                                "carla@correo.com",
                                "Sugiero habilitar un buzón digital adicional en el portal de trámites municipales.",
                                false,
                                captcha,
                                false)))
                .andExpect(status().isCreated())
                .andReturn();
        String codigo = objectMapper.readTree(creado.getResponse().getContentAsString())
                .get("codigoSeguimiento")
                .asText();

        MockMultipartFile pdf = new MockMultipartFile(
                "archivos", "evidencia.pdf", "application/pdf", "%PDF-1.4 prueba".getBytes());
        MockMultipartFile exe = new MockMultipartFile(
                "archivos", "malware.exe", "application/octet-stream", "MZ".getBytes());

        mockMvc.perform(multipart("/api/casos/publico/" + codigo + "/documentos")
                        .file(pdf)
                        .file(exe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivosSubidos[0].nombreArchivo").value("evidencia.pdf"))
                .andExpect(jsonPath("$.rechazados[0].nombre").value("malware.exe"));
    }

    private Captcha captcha() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/casos/captcha"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Matcher matcher = SUMA.matcher(body.get("pregunta").asText());
        if (!matcher.find()) {
            throw new IllegalStateException("No se pudo interpretar el captcha");
        }
        int suma = Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2));
        return new Captcha(body.get("captchaId").asText(), String.valueOf(suma));
    }

    private String payload(
            String tipo,
            String nombre,
            String email,
            String descripcion,
            boolean anonimo,
            Captcha captcha,
            boolean forzar) {
        return """
                {
                  "tipoCaso":"%s",
                  "nombreCiudadano":"%s",
                  "email":"%s",
                  "telefono":"55551234",
                  "areaDependencia":"SERVICIOS",
                  "descripcion":"%s",
                  "esAnonimo":%s,
                  "aceptaPrivacidad":true,
                  "captchaId":"%s",
                  "captchaRespuesta":"%s",
                  "forzarRegistro":%s
                }
                """.formatted(tipo, nombre, email, descripcion, anonimo, captcha.id(), captcha.respuesta(), forzar);
    }

    private record Captcha(String id, String respuesta) {
    }
}
