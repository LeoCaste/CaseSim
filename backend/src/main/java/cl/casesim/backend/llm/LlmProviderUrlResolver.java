package cl.casesim.backend.llm;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class LlmProviderUrlResolver {

    private static final String OPENAI_DEFAULT_BASE = "https://api.openai.com/v1";
    private static final String GROQ_DEFAULT_BASE = "https://api.groq.com/openai/v1";
    private static final String OPENROUTER_DEFAULT_BASE = "https://openrouter.ai/api/v1";
    private static final String OLLAMA_DEFAULT_BASE = "http://localhost:11434/v1";
    private static final String GEMINI_DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String ANTHROPIC_DEFAULT_BASE = "https://api.anthropic.com/v1";
    private static final String OPENAI_PATH = "/chat/completions";
    private static final String GROQ_PATH = "/chat/completions";
    private static final String ANTHROPIC_PATH = "/messages";

    public String resolve(String provider, String configuredBaseUrl) {
        if (LlmProviderSupport.GEMINI.equals(LlmProviderSupport.normalize(provider))) {
            throw new LlmClientException("Use resolveGeminiGenerateContentUrl para provider gemini.");
        }
        String baseUrl = resolveBaseUrl(provider, configuredBaseUrl);
        String path = resolveChatCompletionsPath(provider);
        return joinBaseAndPath(baseUrl, path);
    }

    public String resolveGeminiGenerateContentUrl(String configuredBaseUrl, String model) {
        String normalizedModel = StringUtils.hasText(model) ? model.trim() : "gemini-2.5-flash-lite";
        String encodedModel = URLEncoder.encode(normalizedModel, StandardCharsets.UTF_8);
        String baseUrl = resolveBaseUrl(LlmProviderSupport.GEMINI, configuredBaseUrl);
        return trimTrailingSlash(baseUrl) + "/" + encodedModel + ":generateContent";
    }

    public String resolveBaseUrl(String provider, String configuredBaseUrl) {
        String normalizedProvider = LlmProviderSupport.normalize(provider);
        String configured = StringUtils.hasText(configuredBaseUrl) ? configuredBaseUrl.trim() : "";
        return switch (normalizedProvider) {
            case LlmProviderSupport.OPENAI, LlmProviderSupport.OPENAI_COMPATIBLE -> normalizeOpenAiBase(configured);
            case LlmProviderSupport.GROQ -> normalizeGroqBase(configured);
            case LlmProviderSupport.OPENROUTER -> OPENROUTER_DEFAULT_BASE;
            case LlmProviderSupport.OLLAMA -> normalizeDefaultOpenAiCompatibleBase(configured, OLLAMA_DEFAULT_BASE);
            case LlmProviderSupport.GEMINI -> normalizeGeminiBase(configured);
            case LlmProviderSupport.ANTHROPIC -> normalizeAnthropicBase(configured);
            default -> throw new LlmClientException("Proveedor no soportado para resolver URL: " + normalizedProvider);
        };
    }

    public String resolveChatCompletionsPath(String provider) {
        String normalizedProvider = LlmProviderSupport.normalize(provider);
        return switch (normalizedProvider) {
            case LlmProviderSupport.OPENAI, LlmProviderSupport.OPENAI_COMPATIBLE -> OPENAI_PATH;
            case LlmProviderSupport.GROQ -> GROQ_PATH;
            case LlmProviderSupport.OPENROUTER, LlmProviderSupport.OLLAMA -> OPENAI_PATH;
            case LlmProviderSupport.ANTHROPIC -> ANTHROPIC_PATH;
            default -> throw new LlmClientException("Proveedor no soportado para path de chat completions: " + normalizedProvider);
        };
    }

    String normalizeAnthropicBase(String configured) {
        if (!StringUtils.hasText(configured)) {
            return ANTHROPIC_DEFAULT_BASE;
        }
        String normalized = trimTrailingSlash(configured);
        if (normalized.endsWith("/messages")) {
            return normalized.substring(0, normalized.length() - "/messages".length());
        }
        return normalized;
    }

    String normalizeDefaultOpenAiCompatibleBase(String configured, String defaultBase) {
        if (!StringUtils.hasText(configured)) {
            return defaultBase;
        }
        String normalized = trimTrailingSlash(configured);
        if (normalized.endsWith("/chat/completions")) {
            return normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized;
    }

    String normalizeOpenAiBase(String configured) {
        if (!StringUtils.hasText(configured)) {
            return OPENAI_DEFAULT_BASE;
        }
        String normalized = trimTrailingSlash(configured);
        if (isGroqHost(normalized)) {
            return OPENAI_DEFAULT_BASE;
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized;
    }

    String normalizeGroqBase(String configured) {
        if (!StringUtils.hasText(configured)) {
            return GROQ_DEFAULT_BASE;
        }
        String normalized = trimTrailingSlash(configured);
        if (isOpenAiHost(normalized)) {
            return GROQ_DEFAULT_BASE;
        }
        try {
            URI uri = new URI(normalized);
            String path = uri.getPath() == null ? "" : trimTrailingSlash(uri.getPath().replaceAll("/+", "/"));

            if (path.endsWith("/chat/completions")) {
                path = path.substring(0, path.length() - "/chat/completions".length());
            }

            if (!StringUtils.hasText(path)) {
                path = "/openai/v1";
            } else if (path.endsWith("/openai")) {
                path = path + "/v1";
            } else if (path.endsWith("/v1") && !path.contains("/openai/")) {
                path = path.substring(0, path.length() - "/v1".length()) + "/openai/v1";
            } else if (!path.endsWith("/openai/v1")) {
                path = path + "/openai/v1";
            }

            URI normalizedUri = new URI(uri.getScheme(), uri.getAuthority(), path, uri.getQuery(), uri.getFragment());
            return trimTrailingSlash(normalizedUri.toString());
        } catch (URISyntaxException ex) {
            return GROQ_DEFAULT_BASE;
        }
    }

    String normalizeGeminiBase(String configured) {
        if (!StringUtils.hasText(configured)) {
            return GEMINI_DEFAULT_BASE;
        }
        String normalized = trimTrailingSlash(configured);

        int modelSegment = normalized.lastIndexOf("/models/");
        if (modelSegment > -1) {
            return normalized.substring(0, modelSegment + "/models".length());
        }

        if (normalized.endsWith("/models")) {
            return normalized;
        }

        if (normalized.endsWith(":generateContent")) {
            int actionSeparator = normalized.lastIndexOf(":generateContent");
            String withoutAction = normalized.substring(0, actionSeparator);
            int modelSeparator = withoutAction.lastIndexOf('/');
            if (modelSeparator > -1) {
                String prefix = withoutAction.substring(0, modelSeparator);
                return prefix.endsWith("/models") ? prefix : prefix + "/models";
            }
        }
        return normalized + "/models";
    }

    private String joinBaseAndPath(String baseUrl, String path) {
        String base = trimTrailingSlash(baseUrl);
        String relativePath = path.startsWith("/") ? path : "/" + path;
        return base + relativePath;
    }

    private String trimTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isOpenAiHost(String baseUrl) {
        String host = resolveHost(baseUrl);
        return "openai.com".equals(host) || host.endsWith(".openai.com");
    }

    private boolean isGroqHost(String baseUrl) {
        String host = resolveHost(baseUrl);
        return "groq.com".equals(host) || host.endsWith(".groq.com");
    }

    private String resolveHost(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ex) {
            return "";
        }
    }
}
