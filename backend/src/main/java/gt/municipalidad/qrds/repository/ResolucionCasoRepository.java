package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.ResolucionCaso;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolucionCasoRepository extends JpaRepository<ResolucionCaso, Long> {

    Optional<ResolucionCaso> findByCasoIdAndVigenteTrue(Long casoId);
}
