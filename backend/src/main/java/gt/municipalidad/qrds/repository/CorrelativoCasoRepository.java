package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.CorrelativoCaso;
import gt.municipalidad.qrds.entity.CorrelativoCasoId;
import gt.municipalidad.qrds.entity.TipoCaso;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorrelativoCasoRepository extends JpaRepository<CorrelativoCaso, CorrelativoCasoId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CorrelativoCaso c where c.tipoCaso = :tipo and c.anio = :anio")
    Optional<CorrelativoCaso> bloquear(@Param("tipo") TipoCaso tipo, @Param("anio") int anio);
}
