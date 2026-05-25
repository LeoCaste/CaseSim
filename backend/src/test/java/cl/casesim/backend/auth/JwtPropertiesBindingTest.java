package cl.casesim.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("security.jwt.secret=test-secret-key-1234567890");

    @Test
    void shouldUseDefaultExpirationWhenNotConfigured() {
        contextRunner.run(context -> {
            JwtService jwtService = context.getBean(JwtService.class);
            assertThat(jwtService.getExpirationMs()).isEqualTo(900_000L);
        });
    }

    @Test
    void shouldHonorConfiguredExpirationAndUseItInTokenGeneration() {
        contextRunner.withPropertyValues("security.jwt.expiration-ms=60000")
                .run(context -> {
                    JwtService jwtService = context.getBean(JwtService.class);
                    assertThat(jwtService.getExpirationMs()).isEqualTo(60_000L);

                    UserPrincipal principal = new UserPrincipal(
                            UUID.randomUUID(),
                            "Profesor Demo",
                            "profesor.demo@ufrontera.cl",
                            "hash",
                            true,
                            Set.of(UserRole.PROFESOR)
                    );

                    String token = jwtService.generateToken(principal);
                    Claims claims = parseClaims(token, "test-secret-key-1234567890");
                    long diffMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

                    assertThat(diffMs).isBetween(59_000L, 61_000L);
                });
    }

    private Claims parseClaims(String token, String secret) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(hashSecret(secret)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private byte[] hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
        @Bean
        JwtService jwtService(JwtProperties jwtProperties) {
            return new JwtService(jwtProperties);
        }
    }
}
