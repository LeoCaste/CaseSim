package cl.casesim.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSetupStateRepository extends JpaRepository<PlatformSetupState, Long> {
}
