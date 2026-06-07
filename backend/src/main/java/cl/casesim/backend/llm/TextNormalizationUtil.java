package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCaseSafetySanitizer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class TextNormalizationUtil {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N} ]");

    private TextNormalizationUtil() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }

        String lowerCased = text.toLowerCase(Locale.ROOT)
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u')
                .replace('ü', 'u');

        return NON_ALPHANUMERIC.matcher(lowerCased).replaceAll(" ").trim();
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public static String extractFactValue(String factLine) {
        int separatorIndex = factLine.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == factLine.length() - 1) {
            return factLine;
        }
        return factLine.substring(separatorIndex + 1).trim();
    }

    public static String maskForLog(String value) {
        if (!hasText(value)) {
            return "<empty>";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120) + "...";
    }

    public static String safeFactPart(String value) {
        return hasText(value) ? value.trim() : "GENERAL";
    }

    public static String safeMetadataValue(Map<String, String> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = metadata.get(key);
            if (hasText(value)) {
                return ClinicalCaseSafetySanitizer.sanitizeCaseHistory(value);
            }
        }
        return null;
    }
}
