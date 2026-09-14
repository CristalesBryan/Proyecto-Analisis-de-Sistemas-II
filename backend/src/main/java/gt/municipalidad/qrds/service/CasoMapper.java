package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CasoDtos.AgenteOpcion;
import gt.municipalidad.qrds.dto.CasoDtos.CasoDetalle;
import gt.municipalidad.qrds.dto.CasoDtos.CasoResumen;
import gt.municipalidad.qrds.dto.CasoDtos.HistorialResumen;
import gt.municipalidad.qrds.dto.CasoDtos.ObservacionResumen;
import gt.municipalidad.qrds.dto.DocumentoDtos.DocumentoResumen;
import gt.municipalidad.qrds.entity.BitacoraCaso;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.ObservacionCaso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.Usuario;
import java.util.List;

public final class CasoMapper {

    private CasoMapper() {
    }

    public static CasoResumen resumen(Caso caso, PlazoService plazos) {
        Usuario agente = caso.getAgenteAsignado();
        return new CasoResumen(
                caso.getId(),
                caso.getCodigoSeguimiento(),
                caso.getTipoCaso().name(),
                caso.getTipoCaso().getEtiqueta(),
                caso.isEsAnonimo() || caso.getNombreCiudadano() == null || caso.getNombreCiudadano().isBlank()
                        ? "Anónimo"
                        : caso.getNombreCiudadano(),
                caso.getAreaDependencia().getCodigo(),
                caso.getAreaDependencia().getNombre(),
                caso.getEstado().name(),
                caso.getFechaRegistro(),
                agente == null ? null : agente.getId(),
                agente == null ? "Sin asignar" : agente.getNombre(),
                caso.getPrioridad().name(),
                caso.getAvancePorcentaje(),
                caso.isEscalado(),
                caso.getFechaLimiteRespuesta(),
                plazos.de(caso.getFechaLimiteRespuesta()));
    }

    public static CasoDetalle detalle(
            Caso caso,
            Usuario consultante,
            List<DocumentoResumen> documentos,
            List<ObservacionCaso> observaciones,
            List<BitacoraCaso> historial,
            PlazoService plazos) {
        CasoResumen base = resumen(caso, plazos);
        return new CasoDetalle(
                base.id(),
                base.codigoSeguimiento(),
                base.tipoCaso(),
                base.tipo(),
                base.ciudadano(),
                base.area(),
                base.areaNombre(),
                base.estado(),
                base.fechaRegistro(),
                base.agenteAsignadoId(),
                base.agenteNombre(),
                base.prioridad(),
                base.avancePorcentaje(),
                base.escalado(),
                base.fechaLimiteRespuesta(),
                base.plazo(),
                caso.getNombreCiudadano(),
                caso.getEmailCiudadano(),
                caso.getTelefono(),
                caso.isEsAnonimo(),
                caso.getDescripcion(),
                caso.getDenunciadoNombre(),
                transiciones(caso, consultante),
                caso.getFechaUltimaActualizacion(),
                documentos,
                observaciones.stream().map(CasoMapper::observacion).toList(),
                historial.stream().map(CasoMapper::evento).toList(),
                caso.getFechaProrroga());
    }

    public static AgenteOpcion agente(Usuario usuario) {
        return new AgenteOpcion(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getAreaDependencia() == null ? null : usuario.getAreaDependencia().getCodigo(),
                usuario.getAreaDependencia() == null ? null : usuario.getAreaDependencia().getNombre());
    }

    private static ObservacionResumen observacion(ObservacionCaso item) {
        return new ObservacionResumen(
                item.getId(),
                item.getTexto(),
                item.getCreadoEn(),
                item.getUsuario().getNombre());
    }

    private static HistorialResumen evento(BitacoraCaso item) {
        return new HistorialResumen(
                item.getTipoEvento().name(),
                item.getEstadoAnterior(),
                item.getEstadoNuevo(),
                item.getDescripcion(),
                item.getFechaHora(),
                item.getUsuario() == null ? "Sistema" : item.getUsuario().getNombre());
    }

    private static List<String> transiciones(Caso caso, Usuario consultante) {
        return caso.getEstado().transiciones().stream()
                .filter(estado -> estado != EstadoCaso.CERRADO)
                .filter(estado -> estado != EstadoCaso.ANULADO || consultante.getRol() == Rol.ADMIN)
                .map(Enum::name)
                .toList();
    }
}
