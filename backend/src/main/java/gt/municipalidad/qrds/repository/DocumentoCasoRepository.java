package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.DocumentoCaso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoCasoRepository extends JpaRepository<DocumentoCaso, Long> {
    @Query("""
            select d from DocumentoCaso d
            where d.caso = :caso and (d.activo is null or d.activo = true)
            order by d.subidoEn desc
            """)
    List<DocumentoCaso> findActivosByCaso(@Param("caso") Caso caso);

    @Query("""
            select count(d) from DocumentoCaso d
            where d.caso = :caso and (d.activo is null or d.activo = true)
            """)
    long countActivos(@Param("caso") Caso caso);

    Optional<DocumentoCaso> findByIdAndCaso(Long id, Caso caso);
}
