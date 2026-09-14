package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.entity.CorrelativoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.repository.CorrelativoCasoRepository;
import java.time.Year;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrelativoService {

    private final CorrelativoCasoRepository correlativoCasoRepository;

    public CorrelativoService(CorrelativoCasoRepository correlativoCasoRepository) {
        this.correlativoCasoRepository = correlativoCasoRepository;
    }

    @Transactional
    public String siguiente(TipoCaso tipoCaso) {
        int anio = Year.now().getValue();
        CorrelativoCaso correlativo = correlativoCasoRepository.bloquear(tipoCaso, anio)
                .orElseGet(() -> correlativoCasoRepository.save(new CorrelativoCaso(tipoCaso, anio, 0)));
        int numero = correlativo.siguiente();
        return tipoCaso.name() + "-" + anio + "-" + String.format("%05d", numero);
    }
}
