package cl.casesim.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class StagingSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(StagingSecurityGuard.class);

    private static final String ENV_STAGING = "staging";
    private static final String ENV_PROD = "prod";
    private static final String JWT_INSECURE_DEFAULT = "dev-secret-change-me";
    private static final String LLM_KEY_INSECURE_DEFAULT = "casesim-default-llm-key";
    private static final String DEV_DB_URL = "jdbc:postgresql://localhost:5433/casesim_db";
    private static final String DEV_DB_USER = "casesim_user";
    private static final String DEV_DB_PASSWORD = "casesim_pass";

    private final String appEnv;
    private final String jwtSecret;
    private final String llmCipherKey;
    private final String corsAllowedOrigins;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String passwordResetMode;
    private final String operationsToken;
    private final String frontendBaseUrl;
    private final String springProfilesActive;

    public StagingSecurityGuard(
            @Value("${app.env:dev}") String appEnv,
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${casesim.security.llm-key}") String llmCipherKey,
            @Value("${app.security.cors.allowed-origins:}") String corsAllowedOrigins,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${casesim.auth.password-reset-mode:DISABLED}") String passwordResetMode,
            @Value("${casesim.auth.operations-token:}") String operationsToken,
            @Value("${app.frontend.base-url:}") String frontendBaseUrl,
            @Value("${spring.profiles.active:}") String springProfilesActive
    ) {
        this.appEnv = appEnv;
        this.jwtSecret = jwtSecret;
        this.llmCipherKey = llmCipherKey;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.passwordResetMode = passwordResetMode;
        this.operationsToken = operationsToken;
        this.frontendBaseUrl = frontendBaseUrl;
        this.springProfilesActive = springProfilesActive;
    }

    @PostConstruct
    void validateStagingSecurity() {
        String normalizedEnv = normalize(appEnv);
        validateAppEnvProfileConsistency(normalizedEnv);

        if (!isStrictEnvironment(normalizedEnv)) {
            warnIfDevDefaultsInUse(normalizedEnv);
            return;
        }

        requireStrongValue(jwtSecret, "JWT_SECRET", JWT_INSECURE_DEFAULT, 32, normalizedEnv);
        requireStrongValue(llmCipherKey, "CASESIM_LLM_CIPHER_KEY", LLM_KEY_INSECURE_DEFAULT, 32, normalizedEnv);

        if (!StringUtils.hasText(corsAllowedOrigins)) {
            throw new IllegalStateException("APP_CORS_ALLOWED_ORIGINS es obligatorio cuando APP_ENV=" + normalizedEnv + ".");
        }

        if (containsWildcardCors(corsAllowedOrigins)) {
            throw new IllegalStateException("APP_CORS_ALLOWED_ORIGINS no puede contener '*' cuando APP_ENV=" + normalizedEnv + ".");
        }

        if (!StringUtils.hasText(datasourceUrl)
                || !StringUtils.hasText(datasourceUsername)
                || !StringUtils.hasText(datasourcePassword)) {
            throw new IllegalStateException("Variables de base de datos son obligatorias cuando APP_ENV=" + normalizedEnv + ".");
        }

        if (isDevDatasourceUrl(datasourceUrl)
                || DEV_DB_USER.equalsIgnoreCase(datasourceUsername.trim())
                || DEV_DB_PASSWORD.equals(datasourcePassword.trim())) {
            throw new IllegalStateException("No se permiten credenciales/URL de base de datos de desarrollo cuando APP_ENV=" + normalizedEnv + ".");
        }

        if ("MANUAL".equalsIgnoreCase(normalize(passwordResetMode))) {
            requireStrongValue(operationsToken, "CASESIM_OPERATIONS_TOKEN", "", 16, normalizedEnv);
            if (!StringUtils.hasText(frontendBaseUrl)) {
                throw new IllegalStateException("APP_FRONTEND_BASE_URL es obligatorio cuando APP_ENV=" + normalizedEnv + " y CASESIM_PASSWORD_RESET_MODE=MANUAL.");
            }
        }
    }

    private void requireStrongValue(String value, String variable, String blockedDefault, int minLength, String normalizedEnv) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variable + " es obligatorio cuando APP_ENV=" + normalizedEnv + ".");
        }

        String normalized = value.trim();
        if (blockedDefault.equals(normalized)) {
            throw new IllegalStateException(variable + " no puede usar valor por defecto inseguro cuando APP_ENV=" + normalizedEnv + ".");
        }

        if (normalized.length() < minLength) {
            throw new IllegalStateException(variable + " debe tener al menos " + minLength + " caracteres cuando APP_ENV=" + normalizedEnv + ".");
        }
    }

    private boolean isStrictEnvironment(String normalizedEnv) {
        return ENV_STAGING.equalsIgnoreCase(normalizedEnv) || ENV_PROD.equalsIgnoreCase(normalizedEnv);
    }

    private void validateAppEnvProfileConsistency(String normalizedAppEnv) {
        Set<String> activeProfiles = Stream.of((springProfilesActive == null ? "" : springProfilesActive).split(","))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        boolean hasStagingProfile = activeProfiles.contains(ENV_STAGING);
        boolean hasProdProfile = activeProfiles.contains(ENV_PROD);

        if (!hasStagingProfile && !hasProdProfile) {
            return;
        }

        if (hasStagingProfile == hasProdProfile) {
            throw new IllegalStateException("APP_ENV and SPRING_PROFILES_ACTIVE are inconsistent. Set both to staging/prod.");
        }

        String requiredEnv = hasProdProfile ? ENV_PROD : ENV_STAGING;
        if (!requiredEnv.equals(normalizedAppEnv)) {
            throw new IllegalStateException("APP_ENV and SPRING_PROFILES_ACTIVE are inconsistent. Set both to staging/prod.");
        }
    }

    private boolean containsWildcardCors(String allowedOrigins) {
        return allowedOrigins.contains("*");
    }

    private boolean isDevDatasourceUrl(String url) {
        String normalized = normalize(url);
        return DEV_DB_URL.equalsIgnoreCase(url.trim())
                || normalized.contains("localhost:5433/casesim_db");
    }

    private void warnIfDevDefaultsInUse(String normalizedEnv) {
        if (JWT_INSECURE_DEFAULT.equals(jwtSecret == null ? null : jwtSecret.trim())) {
            log.warn("APP_ENV={} usando JWT_SECRET por defecto de desarrollo. No usar en staging/prod.", normalizeEnvForLog(normalizedEnv));
        }
        if (LLM_KEY_INSECURE_DEFAULT.equals(llmCipherKey == null ? null : llmCipherKey.trim())) {
            log.warn("APP_ENV={} usando CASESIM_LLM_CIPHER_KEY por defecto de desarrollo. No usar en staging/prod.", normalizeEnvForLog(normalizedEnv));
        }
        if (!StringUtils.hasText(corsAllowedOrigins) || containsWildcardCors(corsAllowedOrigins)) {
            log.warn("APP_ENV={} con CORS permisivo/no configurado para desarrollo. Restringir en staging/prod.", normalizeEnvForLog(normalizedEnv));
        }
        if (!StringUtils.hasText(datasourceUrl)
                || DEV_DB_URL.equalsIgnoreCase(datasourceUrl.trim())
                || DEV_DB_USER.equalsIgnoreCase(datasourceUsername == null ? "" : datasourceUsername.trim())) {
            log.warn("APP_ENV={} usando datasource de desarrollo o incompleto. Requerido definir variables reales en staging/prod.", normalizeEnvForLog(normalizedEnv));
        }
    }

    private String normalizeEnvForLog(String normalizedEnv) {
        return StringUtils.hasText(normalizedEnv) ? normalizedEnv : "dev";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
