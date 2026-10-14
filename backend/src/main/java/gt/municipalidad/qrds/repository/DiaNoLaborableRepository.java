package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.DiaNoLaborable;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaNoLaborableRepository extends JpaRepository<DiaNoLaborable, LocalDate> {
    List<DiaNoLaborable> findAllByOrderByFechaAsc();
}
