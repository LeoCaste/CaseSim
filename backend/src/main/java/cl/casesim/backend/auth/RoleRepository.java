package cl.casesim.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNombreIgnoreCase(String nombre);

    List<Role> findAllByOrderByNombreAsc();

    List<Role> findByNombreIn(Collection<String> nombres);
}
