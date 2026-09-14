package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CasoRepository extends JpaRepository<Caso, Long>, JpaSpecificationExecutor<Caso> {
    Optional<Caso> findByCodigoSeguimientoIgnoreCase(String codigoSeguimiento);

    List<Caso> findByEmailCiudadanoIgnoreCaseAndTipoCasoAndFechaRegistroAfterAndEstadoNotIn(
            String email,
            TipoCaso tipoCaso,
            Instant desde,
            Collection<EstadoCaso> estadosExcluidos);

    @EntityGraph(attributePaths = "areaDependencia")
    List<Caso> findByEmailCiudadanoIgnoreCaseAndEsAnonimoFalseOrderByFechaRegistroDesc(String email);
}
