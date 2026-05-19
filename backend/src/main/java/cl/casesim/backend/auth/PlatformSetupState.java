package cl.casesim.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_setup_state")
public class PlatformSetupState {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "initialized", nullable = false)
    private boolean initialized;

    @Column(name = "initialized_at")
    private LocalDateTime initializedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PlatformSetupState() {
    }

    public PlatformSetupState(Long id, boolean initialized, LocalDateTime initializedAt, LocalDateTime updatedAt, LocalDateTime createdAt) {
        this.id = id;
        this.initialized = initialized;
        this.initializedAt = initializedAt;
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized(LocalDateTime now) {
        this.initialized = true;
        this.initializedAt = now;
        this.updatedAt = now;
    }
}
