package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.DocumentoCaso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoCasoRepository extends JpaRepository<DocumentoCaso, Long> {
    long countByCaso(Caso caso);

    List<DocumentoCaso> findByCasoOrderBySubidoEnDesc(Caso caso);

    Optional<DocumentoCaso> findByIdAndCaso(Long id, Caso caso);
}
