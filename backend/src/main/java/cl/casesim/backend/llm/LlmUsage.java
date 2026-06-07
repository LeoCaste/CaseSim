package cl.casesim.backend.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "uso_llm")
public class LlmUsage {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sesion_id")
    private UUID sessionId;

    @Column(name = "proveedor", nullable = false, length = 80)
    private String provider;

    @Column(name = "modelo", nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int tokensInput;

    @Column(name = "completion_tokens", nullable = false)
    private int tokensOutput;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "latencia_ms")
    private Integer latencyMs;

    @Column(name = "fallback_usado", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "error_detalle")
    private String error;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime createdAt;

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public String getError() {
        return error;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getTokensInput() {
        return tokensInput;
    }

    public int getTokensOutput() {
        return tokensOutput;
    }

    protected LlmUsage() {
        // Constructor requerido por JPA
    }

    public LlmUsage(
            UUID id,
            UUID sessionId,
            String provider,
            String model,
            int tokensInput,
            int tokensOutput,
            int totalTokens,
            Integer latencyMs,
            boolean fallbackUsed,
            String error,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.provider = provider;
        this.model = model;
        this.tokensInput = tokensInput;
        this.tokensOutput = tokensOutput;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.fallbackUsed = fallbackUsed;
        this.error = error;
        this.createdAt = createdAt;
    }
}
