package cl.casesim.backend.llm;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

public class ResponseSafetyFilter {

    public static final String SAFE_FALLBACK = "No entiendo a qué se refiere. Yo venía porque tengo tos y me siento muy cansada.";

    private static final List<Pattern> BLOCK_PATTERNS = List.of(
            Pattern.compile("(soy|como)\\s+(una\\s+)?(ia|i\\.?a\\.?|inteligencia\\s+artificial)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("como\\s+modelo\\s+de\\s+lenguaje", Pattern.CASE_INSENSITIVE),
            Pattern.compile("mi\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("instrucciones?\\s+internas?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("api\\s*key", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(token|clave)\\s*(api)?\\b", Pattern.CASE_INSENSITIVE),

            Pattern.compile("```"),
            Pattern.compile("\\b(public\\s+class|public\\s+static\\s+void|private\\s+final|import\\s+java|function\\s*\\(|def\\s+\\w+\\s*\\(|SELECT\\s+.+\\s+FROM)\\b", Pattern.CASE_INSENSITIVE),

            Pattern.compile("\\bdiagn[oó]stic(o|a)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bevalu(a|aci[oó]n|arte|aci[oó]n\\s+del\\s+estudiante|aci[oó]n\\s+del\\s+alumno)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(soy|act[uú]o\s+como)\\s+(m[eé]dico|doctora?|doctor|profesor(a)?|docente)\\b", Pattern.CASE_INSENSITIVE),

            Pattern.compile("razonamiento\\s+cl[ií]nico", Pattern.CASE_INSENSITIVE),
            Pattern.compile("diagn[oó]stico\\s+diferencial", Pattern.CASE_INSENSITIVE),
            Pattern.compile("hip[oó]tesis\\s+diagn[oó]stica", Pattern.CASE_INSENSITIVE),
            Pattern.compile("plan\\s+terap[eé]utico", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> STRICT_BLOCK_PATTERNS = List.of(
            Pattern.compile("\\b(asistente\\s+virtual|chatbot)\\b", Pattern.CASE_INSENSITIVE)
    );

    public String applyOrFallback(String content) {
        return applyOrFallback(content, false, SAFE_FALLBACK);
    }

    public String applyOrFallback(String content, boolean strictMode) {
        return applyOrFallback(content, strictMode, SAFE_FALLBACK);
    }

    public String applyOrFallback(String content, boolean strictMode, String fallbackResponse) {
        String effectiveFallback = StringUtils.hasText(fallbackResponse) ? fallbackResponse.trim() : SAFE_FALLBACK;
        if (!StringUtils.hasText(content)) {
            return effectiveFallback;
        }

        // Las salvaguardas base se aplican siempre; strictMode solo agrega bloqueos extra.
        String normalized = content.trim();
        for (Pattern pattern : BLOCK_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return effectiveFallback;
            }
        }

        if (strictMode) {
            for (Pattern pattern : STRICT_BLOCK_PATTERNS) {
                if (pattern.matcher(normalized).find()) {
                    return effectiveFallback;
                }
            }
        }
        return normalized;
    }
}
