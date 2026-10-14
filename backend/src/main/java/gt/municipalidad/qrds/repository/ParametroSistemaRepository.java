package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.ParametroSistema;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroSistemaRepository extends JpaRepository<ParametroSistema, String> {
    List<ParametroSistema> findByGrupoOrderByClaveAsc(String grupo);

    List<ParametroSistema> findAllByOrderByGrupoAscClaveAsc();
}
