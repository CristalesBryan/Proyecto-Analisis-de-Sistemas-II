package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.BitacoraCaso;
import gt.municipalidad.qrds.entity.Caso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BitacoraCasoRepository
        extends JpaRepository<BitacoraCaso, Long>, JpaSpecificationExecutor<BitacoraCaso> {
    List<BitacoraCaso> findByCasoOrderByFechaHoraDesc(Caso caso);
}
