package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.EstadoReporte;
import gt.municipalidad.qrds.entity.FormatoReporte;
import gt.municipalidad.qrds.entity.ReporteGenerado;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReporteGeneradoRepository extends JpaRepository<ReporteGenerado, Long> {

    @EntityGraph(attributePaths = "solicitadoPor")
    List<ReporteGenerado> findBySolicitadoPor_IdOrderByCreadoEnDesc(Long usuarioId);

    @Override
    @EntityGraph(attributePaths = {"solicitadoPor", "solicitadoPor.areaDependencia"})
    Optional<ReporteGenerado> findById(Long id);

    @EntityGraph(attributePaths = "solicitadoPor")
    Optional<ReporteGenerado>
            findFirstBySolicitadoPor_IdAndTipoReporteAndFormatoAndFiltrosHashAndEstadoAndExpiraEnAfterOrderByCreadoEnDesc(
                    Long usuarioId,
                    String tipoReporte,
                    FormatoReporte formato,
                    String filtrosHash,
                    EstadoReporte estado,
                    Instant ahora);
}
