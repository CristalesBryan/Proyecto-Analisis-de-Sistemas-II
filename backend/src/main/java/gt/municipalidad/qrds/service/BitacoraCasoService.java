package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.entity.BitacoraCaso;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoEventoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.repository.BitacoraCasoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BitacoraCasoService {

    private final BitacoraCasoRepository repository;

    public BitacoraCasoService(BitacoraCasoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(Caso caso, Usuario usuario, TipoEventoCaso tipoEvento, String descripcion, String ip) {
        registrar(caso, usuario, tipoEvento, descripcion, ip, null, null);
    }

    @Transactional
    public void registrar(
            Caso caso,
            Usuario usuario,
            TipoEventoCaso tipoEvento,
            String descripcion,
            String ip,
            EstadoCaso estadoAnterior,
            EstadoCaso estadoNuevo) {
        repository.save(new BitacoraCaso(caso, usuario, tipoEvento, descripcion, ip, estadoAnterior, estadoNuevo));
    }
}
