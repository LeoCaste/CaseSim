package cl.casesim.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class StagingSecurityGuard {

    private static final String ENV_STAGING = "staging";
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

    public StagingSecurityGuard(
            @Value("${app.env:dev}") String appEnv,
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${casesim.security.llm-key}") String llmCipherKey,
            @Value("${app.security.cors.allowed-origins:}") String corsAllowedOrigins,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword
    ) {
        this.appEnv = appEnv;
        this.jwtSecret = jwtSecret;
        this.llmCipherKey = llmCipherKey;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
    }

    @PostConstruct
    void validateStagingSecurity() {
        if (!ENV_STAGING.equalsIgnoreCase(normalize(appEnv))) {
            return;
        }

        requireStrongValue(jwtSecret, "JWT_SECRET", JWT_INSECURE_DEFAULT, 32);
        requireStrongValue(llmCipherKey, "CASESIM_LLM_CIPHER_KEY", LLM_KEY_INSECURE_DEFAULT, 32);

        if (!StringUtils.hasText(corsAllowedOrigins)) {
            throw new IllegalStateException("APP_CORS_ALLOWED_ORIGINS es obligatorio cuando APP_ENV=staging.");
        }

        if (!StringUtils.hasText(datasourceUrl)
                || !StringUtils.hasText(datasourceUsername)
                || !StringUtils.hasText(datasourcePassword)) {
            throw new IllegalStateException("Variables de base de datos son obligatorias cuando APP_ENV=staging.");
        }

        if (DEV_DB_URL.equalsIgnoreCase(datasourceUrl.trim())
                || DEV_DB_USER.equalsIgnoreCase(datasourceUsername.trim())
                || DEV_DB_PASSWORD.equals(datasourcePassword.trim())) {
            throw new IllegalStateException("No se permiten credenciales/URL de base de datos de desarrollo cuando APP_ENV=staging.");
        }
    }

    private void requireStrongValue(String value, String variable, String blockedDefault, int minLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variable + " es obligatorio cuando APP_ENV=staging.");
        }

        String normalized = value.trim();
        if (blockedDefault.equals(normalized)) {
            throw new IllegalStateException(variable + " no puede usar valor por defecto inseguro cuando APP_ENV=staging.");
        }

        if (normalized.length() < minLength) {
            throw new IllegalStateException(variable + " debe tener al menos " + minLength + " caracteres cuando APP_ENV=staging.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
