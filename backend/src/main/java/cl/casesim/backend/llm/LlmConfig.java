package cl.casesim.backend.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;

    @Column(name = "patient_behavior_rules", nullable = false)
    private String patientBehaviorRules;

    @Column(name = "no_info_response", nullable = false, length = 500)
    private String noInfoResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "reveal_strategy", nullable = false, length = 20)
    private RevealStrategy revealStrategy;

    @Column(name = "max_history_messages", nullable = false)
    private int maxHistoryMessages;

    @Column(name = "temperature", nullable = false)
    private double temperature;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens;

    @Column(name = "enabled_safety_filter", nullable = false)
    private boolean enabledSafetyFilter;

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
            LocalDateTime updatedAt,
            String systemPrompt,
            String patientBehaviorRules,
            String noInfoResponse,
            RevealStrategy revealStrategy,
            int maxHistoryMessages,
            double temperature,
            int maxTokens,
            boolean enabledSafetyFilter
    ) {
        this.id = id;
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.apiKeySecret = apiKeySecret;
        this.updatedAt = updatedAt;
        this.systemPrompt = systemPrompt;
        this.patientBehaviorRules = patientBehaviorRules;
        this.noInfoResponse = noInfoResponse;
        this.revealStrategy = revealStrategy;
        this.maxHistoryMessages = maxHistoryMessages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.enabledSafetyFilter = enabledSafetyFilter;
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

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getPatientBehaviorRules() {
        return patientBehaviorRules;
    }

    public String getNoInfoResponse() {
        return noInfoResponse;
    }

    public RevealStrategy getRevealStrategy() {
        return revealStrategy;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isEnabledSafetyFilter() {
        return enabledSafetyFilter;
    }

    public void update(
            String provider,
            String model,
            String baseUrl,
            boolean enabled,
            String apiKeySecret,
            LocalDateTime updatedAt,
            String systemPrompt,
            String patientBehaviorRules,
            String noInfoResponse,
            RevealStrategy revealStrategy,
            int maxHistoryMessages,
            double temperature,
            int maxTokens,
            boolean enabledSafetyFilter
    ) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.apiKeySecret = apiKeySecret;
        this.updatedAt = updatedAt;
        this.systemPrompt = systemPrompt;
        this.patientBehaviorRules = patientBehaviorRules;
        this.noInfoResponse = noInfoResponse;
        this.revealStrategy = revealStrategy;
        this.maxHistoryMessages = maxHistoryMessages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.enabledSafetyFilter = enabledSafetyFilter;
    }
}
