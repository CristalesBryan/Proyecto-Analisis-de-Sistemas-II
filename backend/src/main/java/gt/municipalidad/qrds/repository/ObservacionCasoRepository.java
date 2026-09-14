package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.ObservacionCaso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservacionCasoRepository extends JpaRepository<ObservacionCaso, Long> {
    List<ObservacionCaso> findByCasoOrderByCreadoEnDesc(Caso caso);
}
