package cl.casesim.backend.sessions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SessionRevealedFactRepository extends JpaRepository<SessionRevealedFact, UUID> {

    List<SessionRevealedFact> findBySessionId(UUID sessionId);

    boolean existsBySessionIdAndFactId(UUID sessionId, UUID factId);

    @Query("select r.factId from SessionRevealedFact r where r.sessionId = :sessionId")
    Set<UUID> findFactIdsBySessionId(@Param("sessionId") UUID sessionId);
}
