package cl.casesim.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static cl.casesim.backend.llm.TextNormalizationUtil.hasText;

@Service
public class LlmInteractionMetricsService {

    private static final Logger log = LoggerFactory.getLogger(LlmInteractionMetricsService.class);

    private final LlmUsageService llmUsageService;

    public LlmInteractionMetricsService(LlmUsageService llmUsageService) {
        this.llmUsageService = llmUsageService;
    }

    public void safeRegisterUsage(
            UUID sessionId,
            String provider,
            String model,
            int promptTokens,
            int completionTokens,
            Integer latencyMs,
            boolean fallbackUsed,
            String error
    ) {
        try {
            llmUsageService.registerCall(
                    sessionId,
                    provider,
                    model,
                    promptTokens,
                    completionTokens,
                    latencyMs,
                    fallbackUsed,
                    error
            );
        } catch (RuntimeException registerError) {
            log.error(
                    "LLM usage metric persistence failed sessionId={} provider={} model={} fallbackUsed={} reason={}",
                    sessionId,
                    provider,
                    model,
                    fallbackUsed,
                    registerError.getMessage()
            );
        }
    }

    public String resolveMetricProvider(LlmResponse providerResponse, String fallbackProvider) {
        if (providerResponse == null || providerResponse.providerResult() == null) {
            return fallbackProvider;
        }
        return hasText(providerResponse.providerResult().provider())
                ? providerResponse.providerResult().provider().trim()
                : fallbackProvider;
    }

    public String resolveMetricModel(LlmResponse providerResponse, String fallbackModel) {
        if (providerResponse == null || providerResponse.providerResult() == null) {
            return fallbackModel;
        }
        return hasText(providerResponse.providerResult().model())
                ? providerResponse.providerResult().model().trim()
                : fallbackModel;
    }

    public Integer resolvePromptTokens(LlmResponse providerResponse) {
        return providerResponse == null || providerResponse.usage() == null
                ? null
                : providerResponse.usage().promptTokens();
    }

    public Integer resolveCompletionTokens(LlmResponse providerResponse) {
        return providerResponse == null || providerResponse.usage() == null
                ? null
                : providerResponse.usage().completionTokens();
    }

    public int countSymptomFacts(List<String> facts) {
        if (facts == null || facts.isEmpty()) {
            return 0;
        }
        return (int) facts.stream()
                .filter(TextNormalizationUtil::hasText)
                .map(TextNormalizationUtil::normalize)
                .filter(text -> text.contains("sintoma") || text.contains("dolor") || text.contains("fiebre") || text.contains("tos") || text.contains("disnea"))
                .count();
    }

    public int estimateTokens(String content) {
        return llmUsageService.estimateTokens(content);
    }
}
