package cl.casesim.backend.llm;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

public class ResponseSafetyFilter {

    public static final String SAFE_FALLBACK = "Entiendo. Cuénteme un poco más sobre eso.";

    private static final List<Pattern> BLOCK_PATTERNS = List.of(
            Pattern.compile("\\bdiagn[oó]stic", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bevalu(a|aci[oó]n|arte)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("instrucciones? internas?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(soy|como)\\s+una\\s+ia", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(soy|como)\\s+un\\s+modelo", Pattern.CASE_INSENSITIVE)
    );

    public String applyOrFallback(String content) {
        if (!StringUtils.hasText(content)) {
            return SAFE_FALLBACK;
        }

        String normalized = content.trim();
        for (Pattern pattern : BLOCK_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return SAFE_FALLBACK;
            }
        }
        return normalized;
    }
}
