package cl.casesim.backend.sessions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, UUID> {
}
