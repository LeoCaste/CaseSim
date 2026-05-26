package cl.casesim.backend.llm;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

public final class OpenRouterModelNormalizer {

    private static final Map<String, String> CLAUDE_ALIASES = Map.of(
            "claude-3.5-haiku", "anthropic/claude-3.5-haiku-20241022",
            "claude-3.5-sonnet", "anthropic/claude-sonnet-4.5",
            "claude-sonnet-4.5", "anthropic/claude-sonnet-4.5",
            "claude-sonnet-4", "anthropic/claude-sonnet-4",
            "claude-3.7-sonnet", "anthropic/claude-3.7-sonnet",
            "anthropic/claude-3.5-haiku", "anthropic/claude-3.5-haiku-20241022",
            "anthropic/claude-3.5-sonnet", "anthropic/claude-sonnet-4.5"
    );

    private OpenRouterModelNormalizer() {
    }

    public static String normalize(String model) {
        if (!StringUtils.hasText(model)) {
            return model;
        }

        String trimmed = model.trim();
        String normalizedKey = trimmed.toLowerCase(Locale.ROOT);
        String mapped = CLAUDE_ALIASES.get(normalizedKey);
        if (mapped != null) {
            return mapped;
        }

        if (!trimmed.contains("/") && normalizedKey.startsWith("claude-")) {
            return "anthropic/" + trimmed;
        }

        return trimmed;
    }
}
