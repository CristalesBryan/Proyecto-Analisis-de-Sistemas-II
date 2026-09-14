package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.SeguimientoCaso;
import gt.municipalidad.qrds.entity.TipoSeguimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoCasoRepository extends JpaRepository<SeguimientoCaso, Long> {
    List<SeguimientoCaso> findByCasoAndTipoOrderByCreadoEnDesc(Caso caso, TipoSeguimiento tipo);

    List<SeguimientoCaso> findByCasoOrderByCreadoEnDesc(Caso caso);

    java.util.Optional<SeguimientoCaso> findByIdAndCaso(Long id, Caso caso);
}
