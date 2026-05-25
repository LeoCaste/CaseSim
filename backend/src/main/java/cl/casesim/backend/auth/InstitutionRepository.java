package cl.casesim.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {
    Optional<Institution> findFirstByOrderByCreadoEnAsc();
}
