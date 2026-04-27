package cl.casesim.backend.llm;

import cl.casesim.backend.llm.dto.LlmConfigResponse;
import cl.casesim.backend.llm.dto.TestConnectionResponse;
import cl.casesim.backend.llm.dto.UpdateLlmConfigRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LlmAdminService {

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
        llmConfigRepository.findFirstByOrderByUpdatedAtDesc().ifPresent(this::applyToRuntimeProperties);
    }

    public LlmConfigResponse getConfig() {
        Optional<LlmConfig> configOpt = llmConfigRepository.findFirstByOrderByUpdatedAtDesc();
        if (configOpt.isPresent()) {
            LlmConfig config = configOpt.get();
            return new LlmConfigResponse(
                    config.getProvider(),
                    config.getModel(),
                    config.getBaseUrl(),
                    config.isEnabled(),
                    StringUtils.hasText(llmApiKeyCipher.decrypt(config.getApiKeySecret())),
                    maskApiKey(llmApiKeyCipher.decrypt(config.getApiKeySecret())),
                    config.getUpdatedAt()
            );
        }

        return new LlmConfigResponse(
                llmProperties.getProvider(),
                llmProperties.getModel(),
                llmProperties.getBaseUrl(),
                llmProperties.isEnabled(),
                llmProperties.hasApiKey(),
                maskApiKey(llmProperties.getApiKey()),
                null
        );
    }

    @Transactional
    public LlmConfigResponse updateConfig(UpdateLlmConfigRequest request) {
        LlmConfig existing = llmConfigRepository.findFirstByOrderByUpdatedAtDesc().orElse(null);
        String resolvedApiKey = resolveApiKey(request.apiKey(), existing);
        String encryptedApiKey = llmApiKeyCipher.encrypt(resolvedApiKey);
        LocalDateTime now = LocalDateTime.now();

        LlmConfig saved;
        if (existing == null) {
            saved = llmConfigRepository.save(new LlmConfig(
                    UUID.randomUUID(),
                    request.provider().trim(),
                    request.model().trim(),
                    request.baseUrl().trim(),
                    request.enabled(),
                    encryptedApiKey,
                    now
            ));
        } else {
            existing.update(
                    request.provider().trim(),
                    request.model().trim(),
                    request.baseUrl().trim(),
                    request.enabled(),
                    encryptedApiKey,
                    now
            );
            saved = llmConfigRepository.save(existing);
        }

        applyToRuntimeProperties(saved);
        return new LlmConfigResponse(
                saved.getProvider(),
                saved.getModel(),
                saved.getBaseUrl(),
                saved.isEnabled(),
                StringUtils.hasText(llmApiKeyCipher.decrypt(saved.getApiKeySecret())),
                maskApiKey(llmApiKeyCipher.decrypt(saved.getApiKeySecret())),
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public TestConnectionResponse testConnection() {
        long startedAt = System.currentTimeMillis();
        String error = null;
        boolean fallbackUsed = false;
        String content = "";

        try {
            if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
                fallbackUsed = true;
                error = "LLM deshabilitado o sin API key.";
                return new TestConnectionResponse(false, error);
            }

            content = llmClient.generateChatCompletion(List.of(new LlmClient.ChatPromptMessage("user", "ping")));
            if (!StringUtils.hasText(content)) {
                fallbackUsed = true;
                error = "Proveedor respondió sin contenido.";
                return new TestConnectionResponse(false, error);
            }
            return new TestConnectionResponse(true, "Conexión exitosa.");
        } catch (RuntimeException ex) {
            fallbackUsed = true;
            error = ex.getMessage();
            return new TestConnectionResponse(false, "No fue posible conectar con el proveedor LLM.");
        } finally {
            int latency = (int) (System.currentTimeMillis() - startedAt);
            llmUsageService.registerCall(
                    null,
                    llmProperties.getProvider(),
                    llmProperties.getModel(),
                    llmUsageService.estimateTokens("ping"),
                    llmUsageService.estimateTokens(content),
                    latency,
                    fallbackUsed,
                    error
            );
        }
    }

    private void applyToRuntimeProperties(LlmConfig config) {
        llmProperties.setProvider(config.getProvider());
        llmProperties.setModel(config.getModel());
        llmProperties.setBaseUrl(config.getBaseUrl());
        llmProperties.setEnabled(config.isEnabled());
        String apiKey = llmApiKeyCipher.decrypt(config.getApiKeySecret());
        llmProperties.setApiKey(apiKey == null ? "" : apiKey);
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
            return "";
        }

        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }

        int visibleChars = 4;
        String suffix = trimmed.substring(trimmed.length() - visibleChars);
        return "*".repeat(Math.max(0, trimmed.length() - visibleChars)) + suffix;
    }
}
