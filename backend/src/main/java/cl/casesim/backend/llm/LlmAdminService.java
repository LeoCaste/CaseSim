package cl.casesim.backend.llm;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.llm.dto.LlmConfigResponse;
import cl.casesim.backend.llm.dto.LlmProviderModelsResponse;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
@Transactional(readOnly = true)
public class LlmAdminService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmAdminService.class);

    private static final int DEFAULT_MAX_HISTORY_MESSAGES = 6;
    private static final int DEFAULT_MAX_TOKENS = 350;
    private static final double DEFAULT_TEMPERATURE = 0.4;
    private static final int MAX_DIAGNOSTIC_DETAIL_LENGTH = 240;
    private static final Pattern GENERIC_MODEL_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,119}$");
    private static final Pattern OPENROUTER_MODEL_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:/-]{1,119}$");
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("(?i)(trace[_-]?id|request[_-]?id|x-request-id)\\s*[:=]\\s*([A-Za-z0-9._:-]{6,128})");

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

    public List<LlmProviderModelsResponse> getAvailableModels(String provider) {
        Map<String, List<String>> modelsByProvider = LlmModelCatalog.modelsByProvider();
        if (!StringUtils.hasText(provider)) {
            return modelsByProvider.entrySet().stream()
                    .map(entry -> new LlmProviderModelsResponse(entry.getKey(), entry.getValue()))
                    .toList();
        }

        String normalizedProvider = normalizeProvider(provider);
        validateProvider(normalizedProvider);
        List<String> models = modelsByProvider.get(normalizedProvider);
        if (models == null) {
            throw new BadRequestException("No hay catálogo de modelos para el provider solicitado.");
        }

        return List.of(new LlmProviderModelsResponse(normalizedProvider, models));
    }

    @Transactional
    public LlmConfigResponse updateConfig(UpdateLlmConfigRequest request) {
        LlmConfig existing = llmConfigRepository.findFirstByOrderByUpdatedAtDesc().orElse(null);
        LocalDateTime now = LocalDateTime.now();
        String provider = normalizeProvider(request.provider());
        validateProvider(provider);
        String model = normalizeAndValidateModel(provider, request.model());
        String baseUrl = resolveBaseUrl(provider, request.baseUrl());
        String resolvedApiKey = resolveApiKey(request.apiKey(), existing);
        ensureApiKeyAvailable(resolvedApiKey);
        String encryptedApiKey = llmApiKeyCipher.encrypt(resolvedApiKey);
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
            llmProperties.setEnabled(false);
            return getConfig();
        }

        LlmConfig config = configOpt.get();
        config.update(
                normalizeProvider(config.getProvider()),
                config.getModel(),
                resolveBaseUrl(normalizeProvider(config.getProvider()), config.getBaseUrl()),
                false,
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
        Integer promptTokens = null;
        Integer completionTokens = null;

        try {
            if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
                fallbackUsed = true;
                error = "LLM deshabilitado o sin API key.";
                return diagnosticResponse(
                        false,
                        error,
                        null,
                        "LLM_DISABLED_OR_MISSING_API_KEY",
                        provider,
                        model,
                        llmProperties.getBaseUrl(),
                        null,
                        error
                );
            }

            LlmResponse response = llmClient.generate(new LlmRequest(List.of(new LlmMessage("user", "ping")), model, null, null));
            content = response == null ? "" : response.content();
            String finalUrl = llmProperties.getBaseUrl();
            if (response != null && response.providerResult() != null) {
                if (StringUtils.hasText(response.providerResult().provider())) {
                    provider = normalizeProvider(response.providerResult().provider());
                }
                if (StringUtils.hasText(response.providerResult().model())) {
                    model = response.providerResult().model().trim();
                }
                if (StringUtils.hasText(response.providerResult().finalUrl())) {
                    finalUrl = response.providerResult().finalUrl();
                }
            }
            if (response != null && response.usage() != null) {
                promptTokens = response.usage().promptTokens();
                completionTokens = response.usage().completionTokens();
            }
            if (!StringUtils.hasText(content)) {
                fallbackUsed = true;
                error = "Proveedor respondió sin contenido.";
                return diagnosticResponse(
                        false,
                        error,
                        null,
                        "EMPTY_PROVIDER_CONTENT",
                        provider,
                        model,
                        finalUrl,
                        null,
                        error
                );
            }
            return diagnosticResponse(
                    true,
                    "Conexión exitosa.",
                    null,
                    null,
                    provider,
                    model,
                    finalUrl,
                    null,
                    null
            );
        } catch (LlmClientException ex) {
            fallbackUsed = true;
            error = sanitizeError(buildMetricErrorForTestConnection(ex));
            LlmProviderError providerError = ex.providerError();
            String errorCode = resolveErrorCode(providerError);
            Integer statusCode = providerError == null ? null : providerError.httpStatus();
            String rawDetail = providerError != null && StringUtils.hasText(providerError.message())
                    ? providerError.message()
                    : ex.getMessage();
            String traceId = resolveTraceId(rawDetail);
            String endpointCandidate = llmProperties.getBaseUrl();
            if (ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("no implementado")) {
                return diagnosticResponse(
                        false,
                        "Proveedor aún no implementado para test de conexión.",
                        statusCode,
                        errorCode,
                        provider,
                        model,
                        endpointCandidate,
                        traceId,
                        rawDetail
                );
            }
            return diagnosticResponse(
                    false,
                    mapTestConnectionErrorMessage(ex),
                    statusCode,
                    errorCode,
                    provider,
                    model,
                    endpointCandidate,
                    traceId,
                    rawDetail
            );
        } catch (RuntimeException ex) {
            fallbackUsed = true;
            error = sanitizeError(ex.getMessage());
            return diagnosticResponse(
                    false,
                    "No fue posible conectar con el proveedor LLM.",
                    null,
                    "UNEXPECTED_RUNTIME_ERROR",
                    provider,
                    model,
                    llmProperties.getBaseUrl(),
                    resolveTraceId(ex.getMessage()),
                    ex.getMessage()
            );
        } finally {
            int latency = (int) (System.currentTimeMillis() - startedAt);
            try {
                llmUsageService.registerCall(
                        null,
                        provider,
                        model,
                        promptTokens == null ? llmUsageService.estimateTokens("ping") : promptTokens,
                        completionTokens == null ? llmUsageService.estimateTokens(content) : completionTokens,
                        latency,
                        fallbackUsed,
                        error
                );
            } catch (RuntimeException metricsException) {
                log.warn("No se pudo registrar métrica de test de conexión LLM: {}", sanitizeError(metricsException.getMessage()));
            }
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
            if (normalized.isEmpty()) {
                return existing == null ? null : llmApiKeyCipher.decrypt(existing.getApiKeySecret());
            }
            return normalized;
        }

        return existing == null ? null : llmApiKeyCipher.decrypt(existing.getApiKeySecret());
    }

    private void ensureApiKeyAvailable(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BadRequestException("La API key es obligatoria cuando no existe una configurada.");
        }
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
        sanitized = sanitized.replaceAll("(?i)(authorization)\\s*[:=]\\s*bearer\\s+[^\\s,;]+", "$1=Bearer ***");
        sanitized = sanitized.replaceAll("(?i)bearer\\s+[a-z0-9_\\-\\.]+", "Bearer ***");
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key|x-goog-api-key|x-api-key|token|secret|password)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
        sanitized = sanitized.replaceAll("(?i)sk-[a-z0-9_\\-]{8,}", "sk-***");
        sanitized = sanitized.replaceAll("(?i)gsk_[a-z0-9_\\-]{8,}", "gsk_***");
        if (sanitized.length() > MAX_DIAGNOSTIC_DETAIL_LENGTH) {
            sanitized = sanitized.substring(0, MAX_DIAGNOSTIC_DETAIL_LENGTH) + "...";
        }
        return sanitized;
    }

    private TestConnectionResponse diagnosticResponse(
            boolean success,
            String message,
            Integer httpStatus,
            String errorCode,
            String provider,
            String model,
            String endpointCandidate,
            String traceId,
            String detail
    ) {
        String normalizedProvider = StringUtils.hasText(provider) ? normalizeProvider(provider) : null;
        String normalizedModel = StringUtils.hasText(model) ? model.trim() : null;
        String endpointPath = resolveEndpointPath(endpointCandidate);
        String resolvedBaseHost = resolveBaseHost(endpointCandidate);
        String safeDetail = sanitizeError(detail);
        String safeTraceId = sanitizeError(traceId);
        int resolvedHttpStatus = resolveHttpStatus(success, httpStatus, errorCode);
        return new TestConnectionResponse(
                success,
                resolvedHttpStatus,
                message,
                message,
                resolvedHttpStatus,
                errorCode,
                normalizedProvider,
                normalizedModel,
                endpointPath,
                resolvedBaseHost,
                safeTraceId,
                safeDetail
        );
    }

    private int resolveHttpStatus(boolean success, Integer providerStatus, String errorCode) {
        if (success) {
            return 200;
        }
        if ("AUTH_ERROR".equals(errorCode) && providerStatus != null) {
            return providerStatus == 403 ? 403 : 401;
        }
        if ("QUOTA_EXCEEDED".equals(errorCode)
                || "RATE_LIMIT".equals(errorCode)) {
            return 429;
        }
        if ("LLM_DISABLED_OR_MISSING_API_KEY".equals(errorCode)
                || "INVALID_REQUEST".equals(errorCode)
                || "MODEL_NOT_FOUND".equals(errorCode)) {
            return 400;
        }
        if ("TIMEOUT".equals(errorCode)
                || "PROVIDER_UNAVAILABLE".equals(errorCode)) {
            return 503;
        }
        if (providerStatus != null) {
            if (providerStatus == 404) {
                // Evita que el frontend interprete "endpoint backend inexistente" cuando
                // realmente es un 404 del proveedor externo.
                return 502;
            }
            if (providerStatus >= 500) {
                return 503;
            }
            if (providerStatus >= 400) {
                return 400;
            }
            return providerStatus;
        }
        return 500;
    }

    private String resolveErrorCode(LlmProviderError providerError) {
        if (providerError == null || providerError.category() == null) {
            return "UNKNOWN_PROVIDER_ERROR";
        }
        return providerError.category().name();
    }

    private String resolveEndpointPath(String url) {
        try {
            if (!StringUtils.hasText(url)) {
                return null;
            }
            String path = new URI(url.trim()).getPath();
            return StringUtils.hasText(path) ? path : null;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private String resolveBaseHost(String url) {
        try {
            if (!StringUtils.hasText(url)) {
                return null;
            }
            return new URI(url.trim()).getHost();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private String resolveTraceId(String detail) {
        if (!StringUtils.hasText(detail)) {
            return null;
        }
        Matcher matcher = TRACE_ID_PATTERN.matcher(detail);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    private String mapTestConnectionErrorMessage(LlmClientException ex) {
        LlmProviderError providerError = ex.providerError();
        if (providerError == null || providerError.category() == null) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("timeout")) {
                return "Timeout al conectar con el proveedor LLM.";
            }
            return "No fue posible conectar con el proveedor LLM.";
        }

        Integer status = providerError.httpStatus();
        String providerDetail = sanitizeProviderDetail(providerError.message());
        String providerDetailLower = providerDetail == null ? "" : providerDetail.toLowerCase(Locale.ROOT);

        if (providerDetailLower.contains("no endpoints found")) {
            return "OpenRouter no tiene endpoints disponibles para el modelo/provider configurado en este momento. "
                    + "Verifique disponibilidad en OpenRouter o cambie de modelo.";
        }

        return switch (providerError.category()) {
            case AUTH_ERROR -> status != null && status == 403
                    ? "Conexión rechazada por permisos del provider (403)."
                    : "API key inválida o no autorizada (401).";
            case QUOTA_EXCEEDED -> "Conexión fallida: cuota del provider agotada (429).";
            case RATE_LIMIT -> "Conexión limitada por rate-limit del provider (429).";
            case MODEL_NOT_FOUND -> appendProviderDetail("Modelo no disponible para el provider configurado (modelo inválido/no encontrado). Verifique el ID exacto del modelo.", providerDetail);
            case INVALID_REQUEST -> appendProviderDetail("Solicitud inválida al provider LLM (400). Revise provider, modelo y configuración.", providerDetail);
            case PROVIDER_UNAVAILABLE -> "Proveedor LLM temporalmente no disponible (5xx).";
            case TIMEOUT -> "Timeout al conectar con el proveedor LLM.";
            default -> "No fue posible conectar con el proveedor LLM.";
        };
    }

    private String sanitizeProviderDetail(String detail) {
        return sanitizeError(detail);
    }

    private String appendProviderDetail(String baseMessage, String detail) {
        if (!StringUtils.hasText(detail)) {
            return baseMessage;
        }
        return baseMessage + " Detail: " + detail;
    }

    private String buildMetricErrorForTestConnection(LlmClientException ex) {
        LlmProviderError providerError = ex.providerError();
        if (providerError == null) {
            return ex.getMessage();
        }
        String category = providerError.category() == null ? "UNKNOWN" : providerError.category().name();
        Integer status = providerError.httpStatus();
        return status == null ? "TEST_CONNECTION|" + category : "TEST_CONNECTION|HTTP_" + status + "|" + category;
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
        if (!LlmProviderSupport.isConfigurableForRealOperation(provider)) {
            throw new BadRequestException("Provider no soportado para operación real. Use: openai, anthropic, groq, gemini u openrouter.");
        }
    }

    private String normalizeAndValidateModel(String provider, String model) {
        if (!StringUtils.hasText(model)) {
            throw new BadRequestException("El modelo es obligatorio.");
        }

        String normalized = model.trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");

        if (LlmProviderSupport.OPENROUTER.equals(provider)) {
            normalized = normalizeOpenRouterModelAlias(normalized);
        }

        if (LlmProviderSupport.ANTHROPIC.equals(provider) && normalized.toLowerCase(Locale.ROOT).startsWith("anthropic/")) {
            throw new BadRequestException("Modelo inválido para Anthropic directo. Use modelo nativo sin prefijo 'anthropic/' (ej: claude-sonnet-4-5).");
        }

        Pattern validationPattern = LlmProviderSupport.OPENROUTER.equals(provider)
                ? OPENROUTER_MODEL_PATTERN
                : GENERIC_MODEL_PATTERN;

        if (!validationPattern.matcher(normalized).matches()) {
            if (LlmProviderSupport.OPENROUTER.equals(provider)) {
                throw new BadRequestException("Modelo inválido para OpenRouter. Use formato provider/model sin espacios (ej: openai/gpt-4.1-mini).");
            }
            throw new BadRequestException("Modelo inválido. Use un identificador sin espacios (ej: gpt-4.1-mini).");
        }

        return normalized;
    }

    private String normalizeOpenRouterModelAlias(String model) {
        return OpenRouterModelNormalizer.normalize(model);
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
