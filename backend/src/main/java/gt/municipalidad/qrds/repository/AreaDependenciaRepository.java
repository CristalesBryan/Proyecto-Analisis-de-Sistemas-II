package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.AreaDependencia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaDependenciaRepository extends JpaRepository<AreaDependencia, Long> {
    Optional<AreaDependencia> findByCodigo(String codigo);

    List<AreaDependencia> findByActivoTrueOrderByNombreAsc();
}
