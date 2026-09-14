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

    public boolean enviarConfirmacionRegistro(Caso caso) {
        if (caso.isEsAnonimo() || caso.getEmailCiudadano() == null || caso.getEmailCiudadano().isBlank()) {
            return false;
        }
        log.info(
                "Confirmación de registro {} enviada a {} (tipo {}, plazo {})",
                caso.getCodigoSeguimiento(),
                caso.getEmailCiudadano(),
                caso.getTipoCaso().getEtiqueta(),
                caso.getTipoCaso().plazoEstimado());
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
