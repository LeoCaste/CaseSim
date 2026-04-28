package cl.casesim.backend.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

    @EntityGraph(attributePaths = "roles")
    List<AppUser> findAllByOrderByNombreAsc();

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByEmailIgnoreCaseAndActivoTrue(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
