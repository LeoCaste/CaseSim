package cl.casesim.backend.llm;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class LlmProviderSupport {

    public static final String OPENAI = "openai";
    public static final String OPENAI_COMPATIBLE = "openai-compatible";
    public static final String ANTHROPIC = "anthropic";
    public static final String GEMINI = "gemini";
    public static final String GROQ = "groq";

    private static final Set<String> SUPPORTED = Set.of(OPENAI, OPENAI_COMPATIBLE, ANTHROPIC, GEMINI, GROQ);

    private LlmProviderSupport() {
    }

    public static String normalize(String provider) {
        if (!StringUtils.hasText(provider)) {
            return OPENAI;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(String provider) {
        return SUPPORTED.contains(normalize(provider));
    }

    public static String defaultBaseUrl(String provider) {
        return switch (normalize(provider)) {
            case OPENAI -> "https://api.openai.com/v1/chat/completions";
            case OPENAI_COMPATIBLE -> "";
            case ANTHROPIC -> "https://api.anthropic.com/v1/messages";
            case GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models";
            case GROQ -> "https://api.groq.com/openai/v1/chat/completions";
            default -> "";
        };
    }

    public static String resolveBaseUrl(String provider, String configuredBaseUrl) {
        if (StringUtils.hasText(configuredBaseUrl)) {
            return configuredBaseUrl.trim();
        }
        return defaultBaseUrl(provider);
    }
}
