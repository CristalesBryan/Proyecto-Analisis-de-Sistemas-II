package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.repository.BitacoraSistemaRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrdsCu10Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BitacoraSistemaRepository bitacoraSistemaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminCreaAgenteQuePuedeIniciarSesionYLaContrasenaQuedaHasheada() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        long areaId = areaId(admin, "ATENCION");
        String correo = "agente.nuevo." + System.nanoTime() + "@municipalidad.gob.gt";
        String clave = "Agente2026!";

        MvcResult creado = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(correo, "AGENTE", areaId, clave)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(correo))
                .andExpect(jsonPath("$.usuario.forzarCambioPassword").value(true))
                .andExpect(jsonPath("$.contrasenaTemporal").doesNotExist())
                .andReturn();
        String cuerpo = creado.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains(clave));
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("passwordHash"));
        long id = objectMapper.readTree(cuerpo).get("usuario").get("id").asLong();

        Usuario guardado = usuarioRepository.findById(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(guardado.getPasswordHash().startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertNotEquals(clave, guardado.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(clave, guardado.getPasswordHash()));

        String bitacora = bitacoraSistemaRepository.findByTipoEventoAndEntidadId("USUARIO_CREADO", id).get(0).getMetadataJson();
        org.junit.jupiter.api.Assertions.assertFalse(bitacora.contains(clave));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + correo + "\",\"password\":\"" + clave + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("AGENTE"))
                .andExpect(jsonPath("$.usuario.forzarCambioPassword").value(true));
    }

    @Test
    void correoDuplicadoNoCreaUsuario() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        long areaId = areaId(admin, "ATENCION");
        long antes = usuarioRepository.count();
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta("agente@municipalidad.gob.gt", "AGENTE", areaId, "Agente2026!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").value("El correo ya está registrado."));
        org.junit.jupiter.api.Assertions.assertEquals(antes, usuarioRepository.count());
    }

    @Test
    void noSePuedeDesactivarAlUnicoAdministradorYElInactivoNoEntra() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        long adminId = usuarioRepository.findByEmailIgnoreCase("admin@municipalidad.gob.gt").orElseThrow().getId();
        mockMvc.perform(put("/api/usuarios/" + adminId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Administrador QRDS","email":"admin@municipalidad.gob.gt","rol":"ADMIN","activo":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ULTIMO_ADMIN"))
                .andExpect(jsonPath("$.mensaje").value("Debe existir al menos un administrador activo."));
        org.junit.jupiter.api.Assertions.assertTrue(
                usuarioRepository.findByEmailIgnoreCase("admin@municipalidad.gob.gt").orElseThrow().isActivo());

        long areaId = areaId(admin, "SERVICIOS");
        String correo = "baja." + System.nanoTime() + "@municipalidad.gob.gt";
        long id = crear(admin, correo, "AGENTE", areaId, "Agente2026!");
        mockMvc.perform(put("/api/usuarios/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Agente Baja","email":"%s","rol":"AGENTE","areaId":%d,"activo":false}
                                """.formatted(correo, areaId)))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(
                bitacoraSistemaRepository.findByTipoEventoAndEntidadId("USUARIO_DESACTIVADO", id).isEmpty());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + correo + "\",\"password\":\"Agente2026!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_INACTIVO"));
    }

    @Test
    void cambioDeRolQuedaEnBitacoraEInvalidaLaSesionAnterior() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        long areaId = areaId(admin, "OBRAS");
        String correo = "rol." + System.nanoTime() + "@municipalidad.gob.gt";
        long id = crear(admin, correo, "AGENTE", areaId, "Agente2026!");
        String sesion = tokenDe(correo, "Agente2026!");

        mockMvc.perform(put("/api/usuarios/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cambio Rol","email":"%s","rol":"SUPERVISOR","areaId":%d,"activo":true}
                                """.formatted(correo, areaId)))
                .andExpect(status().isOk());

        String metadata = bitacoraSistemaRepository.findByTipoEventoAndEntidadId("ROL_CAMBIADO", id).get(0).getMetadataJson();
        org.junit.jupiter.api.Assertions.assertTrue(metadata.contains("AGENTE"));
        org.junit.jupiter.api.Assertions.assertTrue(metadata.contains("SUPERVISOR"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agenteNoAdministraUsuariosYSupervisorNoCreaAdministrador() throws Exception {
        String agente = tokenDe("agente@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + agente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));

        String supervisor = tokenDe("supervisor@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        long areaId = areaId(supervisor, "ATENCION");
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta("admin.falso." + System.nanoTime() + "@municipalidad.gob.gt", "ADMIN", areaId, "Admin2026!")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
        org.junit.jupiter.api.Assertions.assertFalse(
                bitacoraSistemaRepository.findByTipoEvento("INTENTO_DENEGADO").isEmpty());

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rol").value("AGENTE"));
    }

    @Test
    void cambioDeContrasenaPropiaNoQuedaEnClaroYRenuevaLaSesion() throws Exception {
        String admin = tokenDe("admin@municipalidad.gob.gt", DataInitializer.PASSWORD_DEMO);
        long areaId = areaId(admin, "AMBIENTE");
        String correo = "clave." + System.nanoTime() + "@municipalidad.gob.gt";
        long id = crear(admin, correo, "AGENTE", areaId, "Agente2026!");
        String sesion = tokenDe(correo, "Agente2026!");
        MvcResult cambio = mockMvc.perform(post("/api/usuarios/me/cambiar-password")
                        .header("Authorization", "Bearer " + sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actual":"Agente2026!","nueva":"NuevaClave2026!","confirmacion":"NuevaClave2026!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.forzarCambioPassword").value(false))
                .andReturn();
        String cuerpo = cambio.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("NuevaClave2026!"));
        String metadata = bitacoraSistemaRepository
                .findByTipoEventoAndEntidadId("PASSWORD_CAMBIO_PROPIO", id).get(0).getMetadataJson();
        org.junit.jupiter.api.Assertions.assertFalse(metadata.contains("NuevaClave2026!"));
        org.junit.jupiter.api.Assertions.assertFalse(metadata.contains("Agente2026!"));
        String nuevoToken = objectMapper.readTree(cuerpo).get("token").asText();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + sesion))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + nuevoToken))
                .andExpect(status().isOk());
        Usuario guardado = usuarioRepository.findById(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("NuevaClave2026!", guardado.getPasswordHash()));
    }

    private long crear(String token, String correo, String rol, long areaId, String clave) throws Exception {
        MvcResult creado = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(correo, rol, areaId, clave)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(creado.getResponse().getContentAsString()).get("usuario").get("id").asLong();
    }

    private long areaId(String token, String codigo) throws Exception {
        MvcResult areas = mockMvc.perform(get("/api/usuarios/catalogos/areas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode area : objectMapper.readTree(areas.getResponse().getContentAsString())) {
            if (codigo.equals(area.get("codigo").asText())) {
                return area.get("id").asLong();
            }
        }
        throw new IllegalStateException("No está el área " + codigo);
    }

    private String alta(String correo, String rol, long areaId, String clave) {
        return """
                {"nombre":"Usuario de prueba","email":"%s","rol":"%s","areaId":%d,"password":"%s","activo":true}
                """.formatted(correo, rol, areaId, clave);
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
