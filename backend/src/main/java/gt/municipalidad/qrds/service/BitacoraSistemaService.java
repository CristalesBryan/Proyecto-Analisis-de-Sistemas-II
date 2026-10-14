package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.entity.BitacoraSistema;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.repository.BitacoraSistemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BitacoraSistemaService {

    private final BitacoraSistemaRepository repository;

    public BitacoraSistemaService(BitacoraSistemaRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(
            String tipoEvento,
            Usuario usuario,
            Long entidadId,
            String descripcion,
            String ip,
            String metadata) {
        guardar("REPORTES", "REPORTE", tipoEvento, usuario, entidadId, descripcion, ip, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarNueva(
            String dominio,
            String entidadTipo,
            String tipoEvento,
            Usuario usuario,
            Long entidadId,
            String descripcion,
            String ip,
            String metadata) {
        guardar(dominio, entidadTipo, tipoEvento, usuario, entidadId, descripcion, ip, metadata);
    }

    @Transactional
    public void registrarEnCurso(
            String dominio,
            String entidadTipo,
            String tipoEvento,
            Usuario usuario,
            Long entidadId,
            String descripcion,
            String ip,
            String metadata) {
        guardar(dominio, entidadTipo, tipoEvento, usuario, entidadId, descripcion, ip, metadata);
    }

    private void guardar(
            String dominio,
            String entidadTipo,
            String tipoEvento,
            Usuario usuario,
            Long entidadId,
            String descripcion,
            String ip,
            String metadata) {
        repository.save(new BitacoraSistema(
                dominio, entidadTipo, tipoEvento, usuario, entidadId, descripcion, ip, metadata));
    }
}
