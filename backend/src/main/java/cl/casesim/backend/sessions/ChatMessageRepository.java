package cl.casesim.backend.sessions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySesionIdOrderByNumeroTurnoAsc(UUID sesionId);

    @Query("select coalesce(max(m.numeroTurno), 0) from ChatMessage m where m.sesionId = :sesionId")
    Integer findMaxNumeroTurnoBySesionId(@Param("sesionId") UUID sesionId);
}
