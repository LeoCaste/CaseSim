package cl.casesim.backend.llm;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.llm.dto.LlmUsageFilters;
import cl.casesim.backend.llm.dto.LlmSummaryResponse;
import cl.casesim.backend.llm.dto.LlmUsageDailyResponse;
import cl.casesim.backend.llm.dto.LlmUsageStatusFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LlmUsageService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmUsageService.class);

    private static final int MAX_PROVIDER_LENGTH = 80;
    private static final int MAX_MODEL_LENGTH = 100;
    private static final BigDecimal TOKENS_PER_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_INPUT_USD_PER_1K_TOKENS = new BigDecimal("0.00015");
    private static final BigDecimal DEFAULT_OUTPUT_USD_PER_1K_TOKENS = new BigDecimal("0.00060");
    private static final BigDecimal DEFAULT_USD_TO_CLP_RATE = new BigDecimal("950");
    private static final int USD_SCALE = 6;
    private static final int CLP_SCALE = 2;
    private static final int RATE_SCALE = 4;

    private final LlmUsageRepository llmUsageRepository;
    private final BigDecimal inputUsdPer1kTokens;
    private final BigDecimal outputUsdPer1kTokens;
    private final BigDecimal usdToClpRate;

    public LlmUsageService(
            LlmUsageRepository llmUsageRepository,
            @Value("${llm.usage.pricing.input-usd-per-1k:0.00015}") BigDecimal configuredInputUsdPer1kTokens,
            @Value("${llm.usage.pricing.output-usd-per-1k:0.00060}") BigDecimal configuredOutputUsdPer1kTokens,
            @Value("${llm.usage.pricing.usd-to-clp-rate:950}") BigDecimal configuredUsdToClpRate
    ) {
        this.llmUsageRepository = llmUsageRepository;
        this.inputUsdPer1kTokens = normalizeConfiguredValue(configuredInputUsdPer1kTokens, DEFAULT_INPUT_USD_PER_1K_TOKENS);
        this.outputUsdPer1kTokens = normalizeConfiguredValue(configuredOutputUsdPer1kTokens, DEFAULT_OUTPUT_USD_PER_1K_TOKENS);
        this.usdToClpRate = normalizeConfiguredValue(configuredUsdToClpRate, DEFAULT_USD_TO_CLP_RATE)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
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
        if (sessionId == null) {
            log.debug("Se omite persistencia de uso LLM sin sesion_id (provider={}, model={}).", provider, model);
            return;
        }

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
        return mapDailyUsage(llmUsageRepository.findDailyUsage());
    }

    public List<LlmUsageDailyResponse> getDailyUsage(String from, String to, String model, String status) {
        LlmUsageFilters filters = parseFilters(from, to, model, status);
        if (filters.isEmpty()) {
            return getDailyUsage();
        }

        return mapDailyUsage(llmUsageRepository.findDailyUsageFiltered(
                filters.from(),
                filters.to(),
                filters.model(),
                filters.status().dbValue()
        ));
    }

    public LlmSummaryResponse getSummary() {
        LlmUsageSummaryProjection summary = llmUsageRepository.getSummary();
        return mapSummary(summary);
    }

    public LlmSummaryResponse getSummary(String from, String to, String model, String status) {
        LlmUsageFilters filters = parseFilters(from, to, model, status);
        if (filters.isEmpty()) {
            return getSummary();
        }

        LlmUsageSummaryProjection summary = llmUsageRepository.getSummaryFiltered(
                filters.from(),
                filters.to(),
                filters.model(),
                filters.status().dbValue()
        );
        return mapSummary(summary);
    }

    private LlmSummaryResponse mapSummary(LlmUsageSummaryProjection summary) {
        long totalTokens = summary.getTotalTokens() == null ? 0L : summary.getTotalTokens();
        long totalPromptTokens = summary.getTotalPromptTokens() == null ? 0L : summary.getTotalPromptTokens();
        long totalCompletionTokens = summary.getTotalCompletionTokens() == null ? 0L : summary.getTotalCompletionTokens();

        BigDecimal estimatedCostUsd = estimateCostUsd(totalPromptTokens, totalCompletionTokens);
        BigDecimal estimatedCostClp = estimatedCostUsd.multiply(usdToClpRate)
                .setScale(CLP_SCALE, RoundingMode.HALF_UP);

        return new LlmSummaryResponse(
                summary.getTotalCalls(),
                totalTokens,
                summary.getAvgLatencyMs(),
                summary.getFallbackCount(),
                summary.getErrorCount(),
                estimatedCostUsd,
                estimatedCostClp,
                usdToClpRate
        );
    }

    private BigDecimal estimateCostUsd(long totalPromptTokens, long totalCompletionTokens) {
        BigDecimal promptCost = BigDecimal.valueOf(totalPromptTokens)
                .multiply(inputUsdPer1kTokens)
                .divide(TOKENS_PER_THOUSAND, USD_SCALE, RoundingMode.HALF_UP);
        BigDecimal completionCost = BigDecimal.valueOf(totalCompletionTokens)
                .multiply(outputUsdPer1kTokens)
                .divide(TOKENS_PER_THOUSAND, USD_SCALE, RoundingMode.HALF_UP);
        return promptCost.add(completionCost).setScale(USD_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeConfiguredValue(BigDecimal configuredValue, BigDecimal defaultValue) {
        if (configuredValue == null || configuredValue.compareTo(BigDecimal.ZERO) <= 0) {
            return defaultValue;
        }
        return configuredValue;
    }

    private List<LlmUsageDailyResponse> mapDailyUsage(List<LlmUsageDailyProjection> rows) {
        return rows.stream()
                .map(row -> new LlmUsageDailyResponse(
                        row.getUsageDate(),
                        row.getTokensInput(),
                        row.getTokensOutput(),
                        row.getCalls(),
                        row.getAvgLatencyMs()
                ))
                .toList();
    }

    private LlmUsageFilters parseFilters(String from, String to, String model, String status) {
        LocalDate fromDate = parseDate(from, "from");
        LocalDate toDate = parseDate(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("El parámetro from no puede ser posterior a to.");
        }

        String normalizedModel = normalizeModelFilter(model);
        LlmUsageStatusFilter statusFilter = LlmUsageStatusFilter.fromNullable(status);

        return new LlmUsageFilters(fromDate, toDate, normalizedModel, statusFilter);
    }

    private LocalDate parseDate(String rawDate, String parameterName) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeException ex) {
            throw new BadRequestException("El parámetro " + parameterName + " debe tener formato yyyy-MM-dd.");
        }
    }

    private String normalizeModelFilter(String model) {
        if (!StringUtils.hasText(model)) {
            return null;
        }

        String normalized = model.trim();
        if (normalized.length() > MAX_MODEL_LENGTH) {
            throw new BadRequestException("El parámetro model excede el largo máximo permitido (100).");
        }

        return normalized.toLowerCase(Locale.ROOT);
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
