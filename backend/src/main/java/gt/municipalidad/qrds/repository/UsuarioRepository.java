package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = "areaDependencia")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDpi(String dpi);

    @Override
    @EntityGraph(attributePaths = "areaDependencia")
    Optional<Usuario> findById(Long id);

    @EntityGraph(attributePaths = "areaDependencia")
    List<Usuario> findByRolAndActivoTrueAndAreaDependencia_CodigoOrderByNombreAsc(Rol rol, String codigoArea);

    @EntityGraph(attributePaths = "areaDependencia")
    List<Usuario> findByRolAndActivoTrueOrderByNombreAsc(Rol rol);
}
