package cl.casesim.backend.llm;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.llm.dto.LlmConfigResponse;
import cl.casesim.backend.llm.dto.TestConnectionResponse;
import cl.casesim.backend.llm.dto.UpdateLlmConfigRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class LlmAdminService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmAdminService.class);

    private static final int DEFAULT_MAX_HISTORY_MESSAGES = 6;
    private static final int DEFAULT_MAX_TOKENS = 350;
    private static final double DEFAULT_TEMPERATURE = 0.4;
    private static final Pattern GENERIC_MODEL_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,119}$");

    private final LlmConfigRepository llmConfigRepository;
    private final LlmProperties llmProperties;
    private final LlmClient llmClient;
    private final LlmUsageService llmUsageService;
    private final LlmApiKeyCipher llmApiKeyCipher;

    public LlmAdminService(
            LlmConfigRepository llmConfigRepository,
            LlmProperties llmProperties,
            LlmClient llmClient,
            LlmUsageService llmUsageService,
            LlmApiKeyCipher llmApiKeyCipher
    ) {
        this.llmConfigRepository = llmConfigRepository;
        this.llmProperties = llmProperties;
        this.llmClient = llmClient;
        this.llmUsageService = llmUsageService;
        this.llmApiKeyCipher = llmApiKeyCipher;
    }

    @PostConstruct
    public void loadPersistedConfig() {
        llmConfigRepository.findFirstByOrderByUpdatedAtDesc().ifPresent(config -> {
            applyToRuntimeProperties(config);
            logRuntimeBaseUrlNormalizationIfNeeded(normalizeProvider(config.getProvider()), config.getBaseUrl());
        });
    }

    public LlmConfigResponse getConfig() {
        Optional<LlmConfig> configOpt = llmConfigRepository.findFirstByOrderByUpdatedAtDesc();
        if (configOpt.isPresent()) {
            LlmConfig config = configOpt.get();
            return toResponse(config);
        }

        return new LlmConfigResponse(
                llmProperties.getProvider(),
                llmProperties.getModel(),
                LlmProviderSupport.resolveBaseUrl(normalizeProvider(llmProperties.getProvider()), llmProperties.getBaseUrl()),
                llmProperties.isEnabled(),
                llmProperties.hasApiKey(),
                maskApiKey(llmProperties.getApiKey()),
                PromptBuilderService.defaultSystemPrompt(),
                defaultBehaviorRules(llmProperties.getPatientBehaviorRules()),
                defaultNoInfoResponse(llmProperties.getNoInfoResponse()),
                defaultRevealStrategy(llmProperties.getRevealStrategy()),
                sanitizeMaxHistoryMessages(llmProperties.getMaxHistoryMessages()),
                sanitizeTemperature(llmProperties.getTemperature()),
                sanitizeMaxTokens(llmProperties.getMaxTokens()),
                llmProperties.isEnabledSafetyFilter(),
                null
        );
    }

    @Transactional
    public LlmConfigResponse updateConfig(UpdateLlmConfigRequest request) {
        LlmConfig existing = llmConfigRepository.findFirstByOrderByUpdatedAtDesc().orElse(null);
        String resolvedApiKey = resolveApiKey(request.apiKey(), existing);
        String encryptedApiKey = llmApiKeyCipher.encrypt(resolvedApiKey);
        LocalDateTime now = LocalDateTime.now();
        String provider = normalizeProvider(request.provider());
        validateProvider(provider);
        String model = normalizeAndValidateModel(provider, request.model());
        String baseUrl = resolveBaseUrl(provider, request.baseUrl());
        String systemPrompt = defaultSystemPrompt(request.resolvedSystemPrompt());
        String behaviorRules = defaultBehaviorRules(request.resolvedPatientBehaviorRules());
        String noInfoResponse = defaultNoInfoResponse(request.resolvedNoInfoResponse());
        RevealStrategy revealStrategy = defaultRevealStrategy(request.resolvedRevealStrategy());
        int maxHistoryMessages = sanitizeMaxHistoryMessages(request.resolvedMaxHistoryMessages());
        double temperature = sanitizeTemperature(request.resolvedTemperature());
        int maxTokens = sanitizeMaxTokens(request.resolvedMaxTokens());
        boolean enabledSafetyFilter = request.resolvedEnabledSafetyFilter() != null && request.resolvedEnabledSafetyFilter();

        LlmConfig saved;
        if (existing == null) {
            saved = llmConfigRepository.save(new LlmConfig(
                    UUID.randomUUID(),
                    provider,
                    model,
                    baseUrl,
                    request.enabled(),
                    encryptedApiKey,
                    now,
                    systemPrompt,
                    behaviorRules,
                    noInfoResponse,
                    revealStrategy,
                    maxHistoryMessages,
                    temperature,
                    maxTokens,
                    enabledSafetyFilter
            ));
        } else {
            existing.update(
                    provider,
                    model,
                    baseUrl,
                    request.enabled(),
                    encryptedApiKey,
                    now,
                    systemPrompt,
                    behaviorRules,
                    noInfoResponse,
                    revealStrategy,
                    maxHistoryMessages,
                    temperature,
                    maxTokens,
                    enabledSafetyFilter
            );
            saved = llmConfigRepository.save(existing);
        }

        applyToRuntimeProperties(saved);
        return toResponse(saved);
    }

    @Transactional
    public LlmConfigResponse deleteApiKey() {
        Optional<LlmConfig> configOpt = llmConfigRepository.findFirstByOrderByUpdatedAtDesc();
        if (configOpt.isEmpty()) {
            llmProperties.setApiKey("");
            return getConfig();
        }

        LlmConfig config = configOpt.get();
        config.update(
                normalizeProvider(config.getProvider()),
                config.getModel(),
                resolveBaseUrl(normalizeProvider(config.getProvider()), config.getBaseUrl()),
                config.isEnabled(),
                null,
                LocalDateTime.now(),
                defaultSystemPrompt(config.getSystemPrompt()),
                defaultBehaviorRules(config.getPatientBehaviorRules()),
                defaultNoInfoResponse(config.getNoInfoResponse()),
                defaultRevealStrategy(config.getRevealStrategy()),
                sanitizeMaxHistoryMessages(config.getMaxHistoryMessages()),
                sanitizeTemperature(config.getTemperature()),
                sanitizeMaxTokens(config.getMaxTokens()),
                config.isEnabledSafetyFilter()
        );

        LlmConfig saved = llmConfigRepository.save(config);
        applyToRuntimeProperties(saved);
        return toResponse(saved);
    }

    @Transactional
    public TestConnectionResponse testConnection() {
        long startedAt = System.currentTimeMillis();
        String error = null;
        boolean fallbackUsed = false;
        String content = "";
        String provider = normalizeProvider(llmProperties.getProvider());
        String model = llmProperties.getModel();

        try {
            if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
                fallbackUsed = true;
                error = "LLM deshabilitado o sin API key.";
                return new TestConnectionResponse(false, error);
            }

            LlmResponse response = llmClient.generate(new LlmRequest(List.of(new LlmMessage("user", "ping")), model, null, null));
            content = response == null ? "" : response.content();
            if (!StringUtils.hasText(content)) {
                fallbackUsed = true;
                error = "Proveedor respondió sin contenido.";
                return new TestConnectionResponse(false, error);
            }
            return new TestConnectionResponse(true, "Conexión exitosa.");
        } catch (RuntimeException ex) {
            fallbackUsed = true;
            error = sanitizeError(ex.getMessage());
            if (error != null && error.toLowerCase(Locale.ROOT).contains("no implementado")) {
                return new TestConnectionResponse(false, "Proveedor aún no implementado para test de conexión.");
            }
            if (error != null && error.toLowerCase(Locale.ROOT).contains("timeout")) {
                return new TestConnectionResponse(false, "Timeout al conectar con el proveedor LLM.");
            }
            return new TestConnectionResponse(false, "No fue posible conectar con el proveedor LLM.");
        } finally {
            int latency = (int) (System.currentTimeMillis() - startedAt);
            llmUsageService.registerCall(
                    null,
                    provider,
                    model,
                    llmUsageService.estimateTokens("ping"),
                    llmUsageService.estimateTokens(content),
                    latency,
                    fallbackUsed,
                    error
            );
        }
    }

    private void applyToRuntimeProperties(LlmConfig config) {
        String provider = normalizeProvider(config.getProvider());
        llmProperties.setProvider(provider);
        llmProperties.setModel(config.getModel() == null ? "" : config.getModel().trim());
        llmProperties.setBaseUrl(resolveRuntimeBaseUrl(provider, config.getBaseUrl()));
        llmProperties.setEnabled(config.isEnabled());
        String apiKey = llmApiKeyCipher.decrypt(config.getApiKeySecret());
        llmProperties.setApiKey(apiKey == null ? "" : apiKey.trim());
        llmProperties.setSystemPrompt(defaultSystemPrompt(config.getSystemPrompt()));
        llmProperties.setPatientBehaviorRules(defaultBehaviorRules(config.getPatientBehaviorRules()));
        llmProperties.setNoInfoResponse(defaultNoInfoResponse(config.getNoInfoResponse()));
        llmProperties.setRevealStrategy(defaultRevealStrategy(config.getRevealStrategy()));
        llmProperties.setMaxHistoryMessages(sanitizeMaxHistoryMessages(config.getMaxHistoryMessages()));
        llmProperties.setTemperature(sanitizeTemperature(config.getTemperature()));
        llmProperties.setMaxTokens(sanitizeMaxTokens(config.getMaxTokens()));
        llmProperties.setEnabledSafetyFilter(config.isEnabledSafetyFilter());
    }

    private String defaultSystemPrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return PromptBuilderService.defaultSystemPrompt();
        }
        return prompt.trim();
    }

    private String defaultBehaviorRules(String rules) {
        return StringUtils.hasText(rules) ? rules.trim() : "";
    }

    private String defaultNoInfoResponse(String noInfoResponse) {
        return StringUtils.hasText(noInfoResponse) ? noInfoResponse.trim() : "No tengo información asociada a eso.";
    }

    private RevealStrategy defaultRevealStrategy(RevealStrategy revealStrategy) {
        return revealStrategy == null ? RevealStrategy.PROGRESSIVE : revealStrategy;
    }

    private int sanitizeMaxHistoryMessages(Integer maxHistoryMessages) {
        if (maxHistoryMessages == null || maxHistoryMessages < 1) {
            return DEFAULT_MAX_HISTORY_MESSAGES;
        }
        return Math.min(maxHistoryMessages, 30);
    }

    private double sanitizeTemperature(Double temperature) {
        if (temperature == null) {
            return DEFAULT_TEMPERATURE;
        }
        return Math.max(0.0, Math.min(2.0, temperature));
    }

    private int sanitizeMaxTokens(Integer maxTokens) {
        if (maxTokens == null) {
            return DEFAULT_MAX_TOKENS;
        }
        if (maxTokens < 64 || maxTokens > 1024) {
            throw new BadRequestException("maxTokens debe estar entre 64 y 1024.");
        }
        return maxTokens;
    }

    private String resolveApiKey(String requestApiKey, LlmConfig existing) {
        if (requestApiKey != null) {
            String normalized = requestApiKey.trim();
            return normalized.isEmpty() ? null : normalized;
        }

        return existing == null ? null : llmApiKeyCipher.decrypt(existing.getApiKeySecret());
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }

        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }

        int visibleChars = 4;
        String suffix = trimmed.substring(trimmed.length() - visibleChars);
        return "*".repeat(Math.max(0, trimmed.length() - visibleChars)) + suffix;
    }

    private String sanitizeError(String error) {
        if (!StringUtils.hasText(error)) {
            return null;
        }

        String sanitized = error.trim();
        String apiKey = llmProperties.getApiKey();
        if (StringUtils.hasText(apiKey)) {
            sanitized = sanitized.replace(apiKey.trim(), "***");
        }
        return sanitized;
    }

    private String normalizeProvider(String provider) {
        return LlmProviderSupport.normalize(provider);
    }

    private LlmConfigResponse toResponse(LlmConfig config) {
        String provider = normalizeProvider(config.getProvider());
        String decryptedApiKey = llmApiKeyCipher.decrypt(config.getApiKeySecret());
        return new LlmConfigResponse(
                provider,
                config.getModel(),
                LlmProviderSupport.resolveBaseUrl(provider, config.getBaseUrl()),
                config.isEnabled(),
                StringUtils.hasText(decryptedApiKey),
                maskApiKey(decryptedApiKey),
                defaultSystemPrompt(config.getSystemPrompt()),
                defaultBehaviorRules(config.getPatientBehaviorRules()),
                defaultNoInfoResponse(config.getNoInfoResponse()),
                defaultRevealStrategy(config.getRevealStrategy()),
                sanitizeMaxHistoryMessages(config.getMaxHistoryMessages()),
                sanitizeTemperature(config.getTemperature()),
                sanitizeMaxTokens(config.getMaxTokens()),
                config.isEnabledSafetyFilter(),
                config.getUpdatedAt()
        );
    }

    private void validateProvider(String provider) {
        if (!LlmProviderSupport.isSupported(provider)) {
            throw new BadRequestException("Provider no soportado. Use: openai, openai-compatible, anthropic, gemini o groq.");
        }
    }

    private String normalizeAndValidateModel(String provider, String model) {
        if (!StringUtils.hasText(model)) {
            throw new BadRequestException("El modelo es obligatorio.");
        }

        String normalized = model.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");

        if (normalized.startsWith("gpt4")) {
            normalized = "gpt-4" + normalized.substring("gpt4".length());
        }
        if (normalized.startsWith("gpt3.5")) {
            normalized = "gpt-3.5" + normalized.substring("gpt3.5".length());
        }
        if (normalized.endsWith("-min")) {
            normalized = normalized + "i";
        }

        if (!GENERIC_MODEL_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("Modelo inválido. Use un identificador sin espacios (ej: gpt-4.1-mini).");
        }

        if (LlmProviderSupport.OPENAI.equals(provider)
                && !(normalized.startsWith("gpt-") || normalized.startsWith("o"))) {
            throw new BadRequestException("Modelo no válido para OpenAI. Ejemplo esperado: gpt-4.1-mini.");
        }

        return normalized;
    }

    private String resolveBaseUrl(String provider, String baseUrl) {
        String resolved = LlmProviderSupport.resolveBaseUrl(provider, baseUrl);
        if (!StringUtils.hasText(resolved)) {
            throw new BadRequestException("baseUrl es obligatoria para el provider seleccionado.");
        }
        return resolved;
    }

    private String resolveRuntimeBaseUrl(String provider, String configuredBaseUrl) {
        String resolved = resolveBaseUrl(provider, configuredBaseUrl);
        if (!LlmProviderSupport.GROQ.equals(provider)) {
            return resolved;
        }

        if (!isGroqAllowedHost(resolved)) {
            return LlmProviderSupport.defaultBaseUrl(LlmProviderSupport.GROQ);
        }
        return resolved;
    }

    private void logRuntimeBaseUrlNormalizationIfNeeded(String provider, String configuredBaseUrl) {
        if (!LlmProviderSupport.GROQ.equals(provider)) {
            return;
        }
        if (!StringUtils.hasText(configuredBaseUrl) || isGroqAllowedHost(configuredBaseUrl)) {
            return;
        }

        log.info(
                "Normalizando baseUrl en runtime para provider=groq por host incompatible. configuredBaseUrl={} runtimeBaseUrl={}",
                configuredBaseUrl,
                LlmProviderSupport.defaultBaseUrl(LlmProviderSupport.GROQ)
        );
    }

    private boolean isGroqAllowedHost(String baseUrl) {
        try {
            URI uri = new URI(baseUrl.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String normalized = host.trim().toLowerCase(Locale.ROOT);
            return "api.groq.com".equals(normalized)
                    || normalized.endsWith(".groq.com")
                    || "localhost".equals(normalized)
                    || "127.0.0.1".equals(normalized);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
