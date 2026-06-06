package cl.casesim.backend.clinicalcases;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ClinicalCaseDescriptionParser {

    private static final Pattern CASESIM_META_BLOCK = Pattern.compile("(?is)\\[CASESIM_META].*?(?=\\R\\s*\\R|\\z)");
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?im)^\\s*\"?(expectedDiagnosis|expected_diagnosis|diagnosticoEsperado|diagnostico_esperado|diagnóstico esperado|diagnostico esperado|finalDiagnosis|final_diagnosis)\"?\\s*[:=].*$"
    );
    private static final Pattern SIMPLE_KEY_VALUE = Pattern.compile("(?m)^\\s*\"?([A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9_ -]{2,80})\"?\\s*[:=]\\s*\"?([^\"\\r\\n,}]+)\"?\\s*,?\\s*$");
    private static final Pattern JSON_LIKE_KEY_VALUE = Pattern.compile("\"([A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9_ -]{2,80})\"\\s*:\\s*\"([^\"]*)\"");

    private ClinicalCaseDescriptionParser() {
    }

    public static ClinicalCaseDescriptionParts parse(String description) {
        if (!hasText(description)) {
            return new ClinicalCaseDescriptionParts(null, Map.of(), Map.of());
        }

        Map<String, String> legacyMetadata = new LinkedHashMap<>();
        Matcher metaMatcher = CASESIM_META_BLOCK.matcher(description);
        while (metaMatcher.find()) {
            extractMetadata(metaMatcher.group(), legacyMetadata);
        }

        Map<String, String> teachingFields = legacyMetadata.entrySet()
                .stream()
                .filter(entry -> isTeachingKey(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        String clinicalContext = sanitizeClinicalContext(description);
        return new ClinicalCaseDescriptionParts(
                clinicalContext,
                Map.copyOf(legacyMetadata),
                Map.copyOf(teachingFields)
        );
    }

    private static String sanitizeClinicalContext(String description) {
        String withoutMetaBlock = CASESIM_META_BLOCK.matcher(description).replaceAll(" ");
        String withoutSensitiveKeys = SENSITIVE_KEY_VALUE.matcher(withoutMetaBlock).replaceAll(" ");

        String sanitized = Arrays.stream(withoutSensitiveKeys.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !containsSensitiveMetadataMarker(line))
                .collect(Collectors.joining("\n"))
                .trim();

        return sanitized.isBlank() ? null : sanitized;
    }

    private static void extractMetadata(String metaBlock, Map<String, String> target) {
        String content = metaBlock.replaceFirst("(?is)^\\s*\\[CASESIM_META]", "").trim();
        addMatches(SIMPLE_KEY_VALUE.matcher(content), target);
        addMatches(JSON_LIKE_KEY_VALUE.matcher(content), target);
    }

    private static void addMatches(Matcher matcher, Map<String, String> target) {
        while (matcher.find()) {
            String key = normalizeMetadataValue(matcher.group(1));
            String value = normalizeMetadataValue(matcher.group(2));
            if (key != null && value != null) {
                target.putIfAbsent(key, value);
            }
        }
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

    private static boolean isTeachingKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("docente")
                || normalized.contains("teacher")
                || normalized.contains("learning")
                || normalized.contains("objective")
                || normalized.contains("objetivo")
                || normalized.contains("rubric")
                || normalized.contains("rubrica")
                || normalized.contains("rúbrica");
    }

    private static String normalizeMetadataValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
