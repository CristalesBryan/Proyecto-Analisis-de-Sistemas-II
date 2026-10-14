package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.BitacoraSistema;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BitacoraSistemaRepository
        extends JpaRepository<BitacoraSistema, Long>, JpaSpecificationExecutor<BitacoraSistema> {

    List<BitacoraSistema> findByTipoEventoAndEntidadId(String tipoEvento, Long entidadId);

    List<BitacoraSistema> findByTipoEvento(String tipoEvento);
}
