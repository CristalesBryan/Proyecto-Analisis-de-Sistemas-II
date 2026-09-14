package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.BitacoraCaso;
import gt.municipalidad.qrds.entity.Caso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BitacoraCasoRepository extends JpaRepository<BitacoraCaso, Long> {
    List<BitacoraCaso> findByCasoOrderByFechaHoraDesc(Caso caso);
}
