package cl.casesim.backend.clinicalcases;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ClinicalCaseSafetySanitizer {

    public static final String GENERIC_CASE_NAME = "Caso clínico asignado";

    private static final Pattern CASESIM_META_BLOCK = Pattern.compile("(?is)\\[CASESIM_META].*?(?=\\R\\s*\\R|\\z)");
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?im)^\\s*\"?(expectedDiagnosis|expected_diagnosis|diagnosticoEsperado|diagnostico_esperado|diagnóstico esperado|diagnostico esperado|finalDiagnosis|final_diagnosis)\"?\\s*[:=].*$"
    );

    private ClinicalCaseSafetySanitizer() {
    }

    public static String safeCaseTitle() {
        return GENERIC_CASE_NAME;
    }

    public static String sanitizeCaseHistory(String value) {
        if (!hasText(value)) {
            return null;
        }

        String withoutMetaBlock = CASESIM_META_BLOCK.matcher(value).replaceAll(" ");
        String withoutSensitiveKeys = SENSITIVE_KEY_VALUE.matcher(withoutMetaBlock).replaceAll(" ");

        String sanitized = Arrays.stream(withoutSensitiveKeys.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !containsSensitiveMetadataMarker(line))
                .collect(Collectors.joining("\n"))
                .trim();

        return sanitized.isBlank() ? null : sanitized;
    }

    private static boolean containsSensitiveMetadataMarker(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("[casesim_meta]")
                || normalized.contains("expecteddiagnosis")
                || normalized.contains("expected_diagnosis")
                || normalized.contains("diagnostico esperado")
                || normalized.contains("diagnóstico esperado")
                || normalized.contains("finaldiagnosis")
                || normalized.contains("final_diagnosis");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
