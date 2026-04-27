package cl.casesim.backend.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "llm_config")
public class LlmConfig {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 80)
    private String provider;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "api_key_secret")
    private String apiKeySecret;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LlmConfig() {
        // Constructor requerido por JPA
    }

    public LlmConfig(
            UUID id,
            String provider,
            String model,
            String baseUrl,
            boolean enabled,
            String apiKeySecret,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.apiKeySecret = apiKeySecret;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getApiKeySecret() {
        return apiKeySecret;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(String provider, String model, String baseUrl, boolean enabled, String apiKeySecret, LocalDateTime updatedAt) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.apiKeySecret = apiKeySecret;
        this.updatedAt = updatedAt;
    }
}
