package gt.municipalidad.qrds;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.config.DataInitializer;
import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
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
class QrdsCu14Test {

    private static final String MOTIVO =
            "Se transfiere el caso para equilibrar la carga del equipo y dar continuidad a la atención.";

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
    private BitacoraCasoRepository bitacoraCasoRepository;

    @Test
    void supervisorTransfiereSinCambiarElEstadoYQuedaEnElHistorial() throws Exception {
        Usuario destino = asegurarAgente("agente.cu14.atencion@municipalidad.gob.gt", "Agente Apoyo", "ATENCION");
        long casoId = casoAsignado("elena-cu14-ok@correo.com", "ATENCION");
        Caso antes = casoRepository.findById(casoId).orElseThrow();
        String supervisor = tokenDe("supervisor@municipalidad.gob.gt");

        mockMvc.perform(post("/api/casos/" + casoId + "/reasignar")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(destino.getId(), MOTIVO, true, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.estado").value(antes.getEstado().name()))
                .andExpect(jsonPath("$.caso.agenteAsignadoId").value(destino.getId()))
                .andExpect(jsonPath("$.caso.historial[0].tipoEvento").value("REASIGNACION"))
                .andExpect(jsonPath("$.caso.historial[0].descripcion").value(org.hamcrest.Matchers.containsString(MOTIVO)));

        Caso despues = casoRepository.findById(casoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(antes.getEstado(), despues.getEstado());
        org.junit.jupiter.api.Assertions.assertEquals(1, reasignaciones(despues));

        mockMvc.perform(get("/api/casos/" + casoId + "/historial")
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[?(@.tipo == 'REASIGNACION')].resumen")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Agente Apoyo"))));
    }

    @Test
    void repetirLaMismaTransferenciaNoDuplicaElEvento() throws Exception {
        Usuario destino = asegurarAgente("agente.cu14.atencion@municipalidad.gob.gt", "Agente Apoyo", "ATENCION");
        long casoId = casoAsignado("elena-cu14-idem@correo.com", "ATENCION");
        String supervisor = tokenDe("supervisor@municipalidad.gob.gt");
        String body = cuerpo(destino.getId(), MOTIVO, false, false);

        mockMvc.perform(post("/api/casos/" + casoId + "/reasignar")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/casos/" + casoId + "/reasignar")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Seleccione un agente distinto."));

        org.junit.jupiter.api.Assertions.assertEquals(1, reasignaciones(casoRepository.findById(casoId).orElseThrow()));
    }

    @Test
    void rechazaJustificacionCortaCasoSinAgenteCerradoYAgente() throws Exception {
        Usuario destino = asegurarAgente("agente.cu14.atencion@municipalidad.gob.gt", "Agente Apoyo", "ATENCION");
        String supervisor = tokenDe("supervisor@municipalidad.gob.gt");
        long asignado = casoAsignado("elena-cu14-corta@correo.com", "ATENCION");

        mockMvc.perform(post("/api/casos/" + asignado + "/reasignar")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(destino.getId(), "Motivo demasiado breve para transferir.", true, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        long sinAgente = idPublico(registrar("elena-cu14-libre@correo.com", "ATENCION"));
        mockMvc.perform(post("/api/casos/" + sinAgente + "/reasignar")
                        .header("Authorization", "Bearer " + tokenDe("admin@municipalidad.gob.gt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(destino.getId(), MOTIVO, true, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CASO_SIN_AGENTE"));

        long cerrado = casoAsignado("elena-cu14-cerrado@correo.com", "ATENCION");
        Caso caso = casoRepository.findById(cerrado).orElseThrow();
        caso.setEstado(EstadoCaso.CERRADO);
        casoRepository.save(caso);
        mockMvc.perform(post("/api/casos/" + cerrado + "/reasignar")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(destino.getId(), MOTIVO, true, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No se puede reasignar un caso cerrado o anulado."));

        mockMvc.perform(post("/api/casos/" + asignado + "/reasignar")
                        .header("Authorization", "Bearer " + tokenDe("agente@municipalidad.gob.gt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(destino.getId(), MOTIVO, true, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void casoResueltoConservaEstadoYEscalamiento() throws Exception {
        Usuario destino = asegurarAgente("agente.cu14.atencion@municipalidad.gob.gt", "Agente Apoyo", "ATENCION");
        long casoId = casoAsignado("elena-cu14-resuelto@correo.com", "ATENCION");
        Caso caso = casoRepository.findById(casoId).orElseThrow();
        caso.setEstado(EstadoCaso.RESUELTO);
        caso.setEscalado(true);
        casoRepository.save(caso);

        mockMvc.perform(post("/api/casos/" + casoId + "/reasignar")
                        .header("Authorization", "Bearer " + tokenDe("supervisor@municipalidad.gob.gt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(destino.getId(), MOTIVO, true, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.estado").value("RESUELTO"))
                .andExpect(jsonPath("$.caso.escalado").value(true));
    }

    @Test
    void administradorPuedeCambiarElAreaYReasignarVarios() throws Exception {
        Usuario apoyo = asegurarAgente("agente.cu14.atencion@municipalidad.gob.gt", "Agente Apoyo", "ATENCION");
        Usuario obras = asegurarAgente("agente.cu14.obras@municipalidad.gob.gt", "Agente Obras", "OBRAS");
        long uno = casoAsignado("elena-cu14-masivo-1@correo.com", "ATENCION");
        long dos = casoAsignado("elena-cu14-masivo-2@correo.com", "ATENCION");
        long cerrado = casoAsignado("elena-cu14-masivo-3@correo.com", "ATENCION");
        Caso cerradoCaso = casoRepository.findById(cerrado).orElseThrow();
        cerradoCaso.setEstado(EstadoCaso.CERRADO);
        casoRepository.save(cerradoCaso);
        String admin = tokenDe("admin@municipalidad.gob.gt");

        mockMvc.perform(post("/api/casos/" + uno + "/reasignar")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(obras.getId(), MOTIVO, true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.area").value("OBRAS"))
                .andExpect(jsonPath("$.caso.estado").value("EN_REVISION"));

        String masivo = """
                {"ids":[%d,%d],"agenteId":%d,"motivo":"%s","notificar":true}
                """.formatted(dos, cerrado, apoyo.getId(), MOTIVO);
        mockMvc.perform(post("/api/casos/reasignar-masivo")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(masivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[?(@.casoId == %d)].aplicado".formatted(dos), org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.resultados[?(@.casoId == %d)].aplicado".formatted(cerrado), org.hamcrest.Matchers.contains(false)));

        long otroOrigen = casoAsignado("elena-cu14-otro@correo.com", "ATENCION");
        String mixto = """
                {"ids":[%d,%d],"agenteId":%d,"motivo":"%s","notificar":true}
                """.formatted(dos, otroOrigen, apoyo.getId(), MOTIVO);
        mockMvc.perform(post("/api/casos/reasignar-masivo")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mixto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ORIGEN_DISTINTO"));

        mockMvc.perform(post("/api/casos/reasignar-masivo")
                        .header("Authorization", "Bearer " + tokenDe("supervisor@municipalidad.gob.gt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(masivo))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorNoReasignaFueraDeSuArea() throws Exception {
        Usuario obras = asegurarAgente("agente.cu14.obras@municipalidad.gob.gt", "Agente Obras", "OBRAS");
        long casoId = casoAsignado("elena-cu14-area@correo.com", "ATENCION");

        mockMvc.perform(post("/api/casos/" + casoId + "/reasignar")
                        .header("Authorization", "Bearer " + tokenDe("supervisor@municipalidad.gob.gt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(obras.getId(), MOTIVO, true, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERMISO_DENEGADO"));
    }

    private long reasignaciones(Caso caso) {
        return bitacoraCasoRepository.findByCasoOrderByFechaHoraDesc(caso).stream()
                .filter(item -> item.getTipoEvento() == TipoEventoCaso.REASIGNACION)
                .count();
    }

    private long casoAsignado(String email, String area) throws Exception {
        long casoId = idPublico(registrar(email, area));
        long agenteId = usuarioRepository.findByEmailIgnoreCase("agente@municipalidad.gob.gt").orElseThrow().getId();
        mockMvc.perform(post("/api/casos/" + casoId + "/asignar")
                        .header("Authorization", "Bearer " + tokenDe("admin@municipalidad.gob.gt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agenteId\":" + agenteId + "}"))
                .andExpect(status().isOk());
        return casoId;
    }

    private String cuerpo(long agenteId, String motivo, boolean notificar, boolean actualizarArea) {
        return """
                {"agenteId":%d,"motivo":"%s","notificar":%s,"actualizarArea":%s}
                """.formatted(agenteId, motivo, notificar, actualizarArea);
    }

    private Usuario asegurarAgente(String email, String nombre, String area) {
        return usuarioRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            Usuario base = usuarioRepository.findByEmailIgnoreCase("agente@municipalidad.gob.gt").orElseThrow();
            AreaDependencia zona = areaRepository.findByCodigo(area).orElseThrow();
            Usuario nuevo = new Usuario();
            nuevo.setNombre(nombre);
            nuevo.setEmail(email);
            nuevo.setPasswordHash(base.getPasswordHash());
            nuevo.setRol(Rol.AGENTE);
            nuevo.setAreaDependencia(zona);
            nuevo.setActivo(true);
            nuevo.setEmailVerificado(true);
            return usuarioRepository.save(nuevo);
        });
    }

    private long idPublico(String codigo) {
        return casoRepository.findByCodigoSeguimientoIgnoreCase(codigo).orElseThrow().getId();
    }

    private String registrar(String email, String area) throws Exception {
        String body = """
                {
                  "tipoCaso":"S",
                  "nombreCiudadano":"Elena Ruiz",
                  "email":"%s",
                  "areaDependencia":"%s",
                  "descripcion":"Solicito revisar el alumbrado de la avenida municipal porque las luminarias fallan de noche.",
                  "aceptaPrivacidad":true,
                  "recaptchaToken":"test-token"
                }
                """.formatted(email, area);
        MvcResult creado = mockMvc.perform(post("/api/casos/publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(creado.getResponse().getContentAsString()).get("codigoSeguimiento").asText();
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
