package cl.casesim.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final long LOW_EXPIRATION_WARNING_MS = 120_000L;

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(hashSecret(jwtProperties.getSecret()));
        long expirationMs = jwtProperties.getExpirationMs();
        log.info("event=JWT_CONFIG_RESOLVED expirationMs={} expirationMinutes={}", expirationMs, expirationMs / 60_000d);
        if (expirationMs <= LOW_EXPIRATION_WARNING_MS) {
            log.warn("event=JWT_EXPIRATION_LOW expirationMs={} message=JWT expiration is very short; review security.jwt.expiration-ms/JWT_EXPIRATION_MS", expirationMs);
        }
    }

    public String generateToken(UserPrincipal principal) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(principal.getUsername())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claims(Map.of(
                        "uid", principal.getId().toString(),
                        "name", principal.getName(),
                        "roles", principal.getRoles().stream().map(Enum::name).toList()
                ))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isValidToken(String token, UserDetails userDetails) {
        String username = normalizeUsername(extractUsername(token));
        String expectedUsername = normalizeUsername(userDetails.getUsername());
        return !username.isBlank() && username.equals(expectedUsername) && !isTokenExpired(token);
    }

    public List<String> extractRoles(String token) {
        Object rolesClaim = extractAllClaims(token).get("roles");
        if (!(rolesClaim instanceof List<?> rolesRaw)) {
            return Collections.emptyList();
        }

        return rolesRaw.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    public long getExpirationMs() {
        return jwtProperties.getExpirationMs();
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private byte[] hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No fue posible inicializar SHA-256 para JWT secret.", ex);
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
