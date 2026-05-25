package cl.casesim.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private static final Duration ACCESS_TOKEN_EXPIRATION = Duration.ofMinutes(15);
    private static final long ACCESS_TOKEN_EXPIRATION_MS = ACCESS_TOKEN_EXPIRATION.toMillis();

    @NotBlank
    private String secret;

    @Positive(message = "security.jwt.expiration-ms debe ser mayor que 0.")
    private long expirationMs = ACCESS_TOKEN_EXPIRATION_MS;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
