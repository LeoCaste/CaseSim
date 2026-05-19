package cl.casesim.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PasswordResetSettings {

    private final PasswordResetMode mode;
    private final String operationsToken;
    private final boolean allowBootstrapFallback;
    private final String bootstrapToken;
    private final String frontendBaseUrl;

    public PasswordResetSettings(
            @Value("${casesim.auth.password-reset-mode:DISABLED}") String mode,
            @Value("${casesim.auth.operations-token:}") String operationsToken,
            @Value("${casesim.auth.operations-token-allow-bootstrap-fallback:false}") boolean allowBootstrapFallback,
            @Value("${casesim.auth.bootstrap-token:}") String bootstrapToken,
            @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl
    ) {
        this.mode = parseMode(mode);
        this.operationsToken = normalize(operationsToken);
        this.allowBootstrapFallback = allowBootstrapFallback;
        this.bootstrapToken = normalize(bootstrapToken);
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);
    }

    public PasswordResetMode mode() {
        return mode;
    }

    public String resolvedOperationsToken() {
        if (!operationsToken.isBlank()) {
            return operationsToken;
        }
        if (allowBootstrapFallback) {
            return bootstrapToken;
        }
        return "";
    }

    public String frontendBaseUrl() {
        return frontendBaseUrl;
    }

    private PasswordResetMode parseMode(String rawMode) {
        String normalized = normalize(rawMode).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return PasswordResetMode.DISABLED;
        }
        try {
            return PasswordResetMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return PasswordResetMode.DISABLED;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = normalize(value);
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
