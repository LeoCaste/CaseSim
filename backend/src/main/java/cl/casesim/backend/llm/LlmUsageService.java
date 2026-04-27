package cl.casesim.backend.llm;

import cl.casesim.backend.llm.dto.LlmSummaryResponse;
import cl.casesim.backend.llm.dto.LlmUsageDailyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LlmUsageService {

    private static final int MAX_PROVIDER_LENGTH = 80;
    private static final int MAX_MODEL_LENGTH = 100;

    private final LlmUsageRepository llmUsageRepository;

    public LlmUsageService(LlmUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    @Transactional
    public void registerCall(
            UUID sessionId,
            String provider,
            String model,
            int tokensInput,
            int tokensOutput,
            Integer latencyMs,
            boolean fallbackUsed,
            String error
    ) {
        LocalDateTime now = LocalDateTime.now();
        int safeTokensInput = Math.max(tokensInput, 0);
        int safeTokensOutput = Math.max(tokensOutput, 0);
        String safeProvider = normalizeRequiredValue(provider, "unknown", MAX_PROVIDER_LENGTH);
        String safeModel = normalizeRequiredValue(model, "unknown", MAX_MODEL_LENGTH);

        llmUsageRepository.save(new LlmUsage(
                UUID.randomUUID(),
                sessionId,
                safeProvider,
                safeModel,
                safeTokensInput,
                safeTokensOutput,
                safeTokensInput + safeTokensOutput,
                latencyMs,
                fallbackUsed,
                normalizeError(error),
                now
        ));
    }

    public List<LlmUsageDailyResponse> getDailyUsage() {
        return llmUsageRepository.findDailyUsage().stream()
                .map(row -> new LlmUsageDailyResponse(
                        row.getUsageDate(),
                        row.getTokensInput(),
                        row.getTokensOutput(),
                        row.getCalls(),
                        row.getAvgLatencyMs()
                ))
                .toList();
    }

    public LlmSummaryResponse getSummary() {
        LlmUsageSummaryProjection summary = llmUsageRepository.getSummary();

        long totalTokens = summary.getTotalTokens() == null ? 0L : summary.getTotalTokens();
        return new LlmSummaryResponse(
                summary.getTotalCalls(),
                totalTokens,
                summary.getAvgLatencyMs(),
                summary.getFallbackCount(),
                summary.getErrorCount()
        );
    }

    private String normalizeError(String error) {
        if (error == null) {
            return null;
        }

        String normalized = error.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRequiredValue(String value, String fallback, int maxLength) {
        String resolved = StringUtils.hasText(value) ? value.trim() : fallback;
        if (resolved.length() <= maxLength) {
            return resolved;
        }

        return resolved.substring(0, maxLength);
    }

    public int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }

        int normalizedLength = content.trim().length();
        return (int) Math.ceil(normalizedLength / 4.0);
    }
}
