package gt.municipalidad.qrds.config;

import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.CorrelativoCaso;
import gt.municipalidad.qrds.entity.CorrelativoCasoId;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.entity.TipoSeguimiento;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.CorrelativoCasoRepository;
import gt.municipalidad.qrds.repository.SeguimientoCasoRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements CommandLineRunner {

    public static final String PASSWORD_DEMO = "AdminQRDS2026!";

    private final AreaDependenciaRepository areaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CasoRepository casoRepository;
    private final SeguimientoCasoRepository seguimientoRepository;
    private final CorrelativoCasoRepository correlativoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            AreaDependenciaRepository areaRepository,
            UsuarioRepository usuarioRepository,
            CasoRepository casoRepository,
            SeguimientoCasoRepository seguimientoRepository,
            CorrelativoCasoRepository correlativoRepository,
            PasswordEncoder passwordEncoder) {
        this.areaRepository = areaRepository;
        this.usuarioRepository = usuarioRepository;
        this.casoRepository = casoRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.correlativoRepository = correlativoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() == 0) {
            sembrarCatalogosYUsuarios();
        }
        asegurarCiudadanoDemo();
        asegurarCorrelativo();
    }

    private void sembrarCatalogosYUsuarios() {
        AreaDependencia atencion = areaRepository.save(new AreaDependencia("ATENCION", "Atención al Ciudadano"));
        areaRepository.save(new AreaDependencia("SERVICIOS", "Servicios Públicos Municipales"));
        areaRepository.save(new AreaDependencia("OBRAS", "Dirección de Obras Públicas"));
        areaRepository.save(new AreaDependencia("ALCALDIA", "Alcaldía Municipal"));
        areaRepository.save(new AreaDependencia("AMBIENTE", "Dirección de Medio Ambiente"));

        String hash = passwordEncoder.encode(PASSWORD_DEMO);

        Usuario admin = nuevoUsuario("Administrador QRDS", "admin@municipalidad.gob.gt", hash, Rol.ADMIN, null, true);
        Usuario supervisor = nuevoUsuario(
                "Supervisor Atención", "supervisor@municipalidad.gob.gt", hash, Rol.SUPERVISOR, atencion, true);
        Usuario agente = nuevoUsuario(
                "Agente Atención", "agente@municipalidad.gob.gt", hash, Rol.AGENTE, atencion, true);
        nuevoUsuario("Usuario Inactivo", "inactivo@municipalidad.gob.gt", hash, Rol.AGENTE, atencion, false);
        Usuario ciudadano = nuevoUsuario(
                "Ciudadano Demo", "ciudadano@email.com", hash, Rol.CIUDADANO, null, true);
        ciudadano.setTelefono("55551234");
        ciudadano.setDpi("1234567890101");
        ciudadano.setAceptaPrivacidad(true);
        usuarioRepository.save(ciudadano);

        usuarioRepository.save(admin);
        usuarioRepository.save(supervisor);
        usuarioRepository.save(agente);

        Caso demo = new Caso();
        demo.setCodigoSeguimiento("Q-2026-00001");
        demo.setTipoCaso(TipoCaso.Q);
        demo.setNombreCiudadano("Ciudadano Demo");
        demo.setEmailCiudadano("ciudadano@email.com");
        demo.setTelefono("55551234");
        demo.setAreaDependencia(atencion);
        demo.setDescripcion(
                "Solicito revisión del servicio de recolección de basura en mi colonia.");
        demo.setEsAnonimo(false);
        demo.setAceptaPrivacidad(true);
        demo.setEstado(EstadoCaso.EN_PROCESO);
        demo.setAvancePorcentaje(40);
        demo.setAgenteAsignado(agente);
        demo.setFechaRegistro(Instant.parse("2026-08-01T14:00:00Z"));
        demo.setFechaUltimaActualizacion(Instant.parse("2026-08-10T16:30:00Z"));
        demo.setFechaLimiteRespuesta(LocalDate.parse("2026-08-21"));
        casoRepository.save(demo);
        correlativoRepository.save(new CorrelativoCaso(TipoCaso.Q, 2026, 1));

        seguimientoRepository.save(new SeguimientoCaso(
                demo,
                agente,
                TipoSeguimiento.PUBLICA,
                "Caso recibido y en proceso",
                "El área de Servicios Públicos está atendiendo la solicitud. Se programó inspección de ruta.",
                40));
        seguimientoRepository.save(new SeguimientoCaso(
                demo,
                supervisor,
                TipoSeguimiento.INTERNA,
                "Nota interna de supervisión",
                "Esta nota no debe mostrarse en la consulta pública.",
                null));
    }

    private Usuario nuevoUsuario(
            String nombre,
            String email,
            String hash,
            Rol rol,
            AreaDependencia area,
            boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setPasswordHash(hash);
        usuario.setRol(rol);
        usuario.setAreaDependencia(area);
        usuario.setActivo(activo);
        usuario.setEmailVerificado(true);
        return usuarioRepository.save(usuario);
    }

    private void asegurarCiudadanoDemo() {
        if (usuarioRepository.findByEmailIgnoreCase("ciudadano@email.com").isPresent()) {
            return;
        }
        String hash = passwordEncoder.encode(PASSWORD_DEMO);
        Usuario ciudadano = nuevoUsuario(
                "Ciudadano Demo", "ciudadano@email.com", hash, Rol.CIUDADANO, null, true);
        ciudadano.setTelefono("55551234");
        ciudadano.setDpi("1234567890101");
        ciudadano.setAceptaPrivacidad(true);
        usuarioRepository.save(ciudadano);
    }

    private void asegurarCorrelativo() {
        int anio = 2026;
        if (correlativoRepository.findById(new CorrelativoCasoId(TipoCaso.Q, anio)).isPresent()) {
            return;
        }
        int ultimo = casoRepository.findByCodigoSeguimientoIgnoreCase("Q-2026-00001").isPresent() ? 1 : 0;
        correlativoRepository.save(new CorrelativoCaso(TipoCaso.Q, anio, ultimo));
    }
}
