package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
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
class QrdsCu00Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void portalPublicoDisponible() throws Exception {
        mockMvc.perform(get("/api/sistema/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("UP"));
    }

    @Test
    void registraAccesoPublicoSinAutenticacion() throws Exception {
        mockMvc.perform(post("/api/bitacora/acceso-publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accion\":\"ACCESO_PORTAL\",\"resultado\":\"OK\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginExitosoDevuelveJwtYPermisos() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.email").value("admin@municipalidad.gob.gt"))
                .andExpect(jsonPath("$.usuario.permisos").isArray());
    }

    @Test
    void credencialesInvalidasNoRevelanSiElUsuarioExiste() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("noexiste@municipalidad.gob.gt", "mala")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void usuarioInactivoNoIniciaSesion() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("inactivo@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_INACTIVO"));
    }

    @Test
    void sesionYLogoutInvalidanElToken() throws Exception {
        String token = tokenDe("agente@municipalidad.gob.gt");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("AGENTE"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void consultaPublicaNoExponeDatosSensibles() throws Exception {
        mockMvc.perform(get("/api/casos/publico/Q-2026-00001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento").value("Q-2026-00001"))
                .andExpect(jsonPath("$.tipo").value("Queja"))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.emailCiudadano").doesNotExist())
                .andExpect(jsonPath("$.nombreCiudadano").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.descripcion").doesNotExist());
    }

    @Test
    void consultaPublicaDeSeguimientoOcultaNotasInternas() throws Exception {
        mockMvc.perform(get("/api/casos/publico/Q-2026-00001/seguimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seguimientos.length()").value(1))
                .andExpect(jsonPath("$.seguimientos[0].tipo").value("PUBLICA"));
    }

    @Test
    void codigoInexistenteNoFiltraInformacion() throws Exception {
        mockMvc.perform(get("/api/casos/publico/Q-2026-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CASO_NO_ENCONTRADO"));
    }

    @Test
    void meSinTokenEsRechazado() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void captchaPublicoNoSeBloqueaPorTokenInvalido() throws Exception {
        mockMvc.perform(get("/api/casos/captcha").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaId").isNotEmpty());
    }

    private String tokenDe(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(email, DataInitializer.PASSWORD_DEMO)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private String credenciales(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
