package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    @EntityGraph(attributePaths = "areaDependencia")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByRolAndActivoTrueAndIdNot(Rol rol, Long id);

    long countByAreaDependencia_Id(Long areaId);

    boolean existsByDpi(String dpi);

    @Override
    @EntityGraph(attributePaths = "areaDependencia")
    Optional<Usuario> findById(Long id);

    @EntityGraph(attributePaths = "areaDependencia")
    List<Usuario> findByRolAndActivoTrueAndAreaDependencia_CodigoOrderByNombreAsc(Rol rol, String codigoArea);

    @EntityGraph(attributePaths = "areaDependencia")
    List<Usuario> findByRolAndActivoTrueOrderByNombreAsc(Rol rol);

    @Override
    @EntityGraph(attributePaths = "areaDependencia")
    Page<Usuario> findAll(Specification<Usuario> spec, Pageable pageable);
}
