package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.entity.BitacoraAcceso;
import gt.municipalidad.qrds.entity.TipoEventoAcceso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.repository.BitacoraAccesoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BitacoraAccesoService {

    private final BitacoraAccesoRepository repository;

    public BitacoraAccesoService(BitacoraAccesoRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(
            Usuario usuario,
            TipoEventoAcceso tipoEvento,
            String ipCliente,
            String userAgent,
            String resultado,
            String detalle) {
        repository.save(new BitacoraAcceso(usuario, tipoEvento, ipCliente, userAgent, resultado, detalle));
    }
}
