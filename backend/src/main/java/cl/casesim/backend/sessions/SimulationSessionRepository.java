package cl.casesim.backend.sessions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, UUID> {

    Optional<SimulationSession> findByActividadIdAndEstudianteId(UUID actividadId, UUID estudianteId);
}
