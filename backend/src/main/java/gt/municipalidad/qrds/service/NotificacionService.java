package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final ParametroService parametroService;

    public NotificacionService(ParametroService parametroService) {
        this.parametroService = parametroService;
    }

    public boolean enviarConfirmacionRegistro(Caso caso) {
        if (caso.isEsAnonimo() || caso.getEmailCiudadano() == null || caso.getEmailCiudadano().isBlank()) {
            return false;
        }
        log.info(
                "Confirmación de registro {} enviada a {} (tipo {}, plazo {})",
                caso.getCodigoSeguimiento(),
                caso.getEmailCiudadano(),
                caso.getTipoCaso().getEtiqueta(),
                parametroService.textoPlazo(caso.getTipoCaso()));
        return true;
    }

    public boolean notificarResolucion(Caso caso, String tipoResultado) {
        if (caso.isEsAnonimo() || caso.getEmailCiudadano() == null || caso.getEmailCiudadano().isBlank()) {
            return false;
        }
        log.info(
                "Notificación de resolución {} ({}) enviada a {} para el caso {}",
                tipoResultado,
                caso.getEstado(),
                caso.getEmailCiudadano(),
                caso.getCodigoSeguimiento());
        return true;
    }

    public void notificarCambioEstado(Caso caso, EstadoCaso anterior, EstadoCaso nuevo) {
        if (nuevo != EstadoCaso.EN_PROCESO
                && nuevo != EstadoCaso.RESUELTO
                && nuevo != EstadoCaso.CERRADO
                && nuevo != EstadoCaso.ANULADO) {
            return;
        }
        if (caso.isEsAnonimo() || caso.getEmailCiudadano() == null || caso.getEmailCiudadano().isBlank()) {
            return;
        }
        log.info(
                "Notificación de estado {} → {} enviada a {} para el caso {}",
                anterior,
                nuevo,
                caso.getEmailCiudadano(),
                caso.getCodigoSeguimiento());
    }

    public void notificarAgenteAsignado(Caso caso, Usuario agente) {
        log.info(
                "Notificación de asignación de {} enviada a {}",
                caso.getCodigoSeguimiento(),
                agente.getEmail());
    }

    public void notificarReasignacion(Caso caso, Usuario anterior, Usuario nuevo) {
        log.info(
                "Notificación N_REASIGNACION de {} enviada a {} (nuevo responsable)",
                caso.getCodigoSeguimiento(),
                nuevo.getEmail());
        log.info(
                "Notificación N_REASIGNACION de {} enviada a {} (agente anterior)",
                caso.getCodigoSeguimiento(),
                anterior.getEmail());
    }

    public void notificarPrimerAcceso(Usuario usuario) {
        if (usuario == null || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return;
        }
        log.info(
                "Notificación de primer acceso enviada a {}. La contraseña no se incluye en el aviso ni en el registro.",
                usuario.getEmail());
    }

    public void notificarRestablecimiento(Usuario usuario) {
        if (usuario == null || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return;
        }
        log.info(
                "Notificación de restablecimiento enviada a {}. La contraseña no se incluye en el aviso ni en el registro.",
                usuario.getEmail());
    }

    public void avisarDocumentoFaltante(Caso caso, String nombreArchivo) {
        log.info(
                "Aviso al administrador: falta el archivo {} del caso {}. La ruta interna no se incluye.",
                nombreArchivo,
                caso.getCodigoSeguimiento());
    }

    public void notificarReporteListo(Usuario usuario, String tipoReporte) {
        if (usuario == null || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return;
        }
        log.info(
                "Notificación N_REPORTE_LISTO enviada a {} para el reporte {}",
                usuario.getEmail(),
                tipoReporte);
    }

    public boolean notificarSeguimientoPublico(Caso caso, SeguimientoCaso seguimiento) {
        if (caso.isEsAnonimo() || caso.getEmailCiudadano() == null || caso.getEmailCiudadano().isBlank()) {
            return false;
        }
        log.info(
                "Notificación de seguimiento público de {} enviada a {}: {}",
                caso.getCodigoSeguimiento(),
                caso.getEmailCiudadano(),
                seguimiento.getTitulo());
        return true;
    }
}
