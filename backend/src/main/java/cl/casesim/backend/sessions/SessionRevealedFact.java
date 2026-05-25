package cl.casesim.backend.sessions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "sesion_hecho_revelado",
        uniqueConstraints = @UniqueConstraint(name = "uk_sesion_hecho", columnNames = {"sesion_id", "hecho_id"})
)
public class SessionRevealedFact {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sessionId;

    @Column(name = "hecho_id", nullable = false)
    private UUID factId;

    @Column(name = "revelado_en", nullable = false)
    private LocalDateTime revealedAt;

    protected SessionRevealedFact() {
        // Constructor requerido por JPA
    }

    public SessionRevealedFact(UUID id, UUID sessionId, UUID factId, LocalDateTime revealedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.factId = factId;
        this.revealedAt = revealedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getFactId() {
        return factId;
    }

    public LocalDateTime getRevealedAt() {
        return revealedAt;
    }
}
