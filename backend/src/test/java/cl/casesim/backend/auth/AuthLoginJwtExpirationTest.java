package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.LoginRequest;
import cl.casesim.backend.auth.dto.LoginResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthLoginJwtExpirationTest {

    @Test
    void loginShouldIssueTokenWithExpirationCloseToConfigured60Seconds() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-1234567890");
        jwtProperties.setExpirationMs(60_000L);
        JwtService jwtService = new JwtService(jwtProperties);

        UserRepository userRepository = mock(UserRepository.class);
        AuthService authService = new AuthService(userRepository, jwtService, new BCryptPasswordEncoder());

        AppUser user = buildUser(true, "profesor.demo@ufrontera.cl", "unused", Set.of(buildRole("PROFESOR")));
        when(userRepository.findByEmailIgnoreCase("profesor.demo@ufrontera.cl")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest("profesor.demo@ufrontera.cl", null));
        Claims claims = parseClaims(response.token(), jwtProperties.getSecret());
        long diffMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertThat(diffMs).isBetween(59_000L, 61_000L);
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

    private AppUser buildUser(boolean active, String email, String passwordHash, Set<Role> roles) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", UUID.fromString("00000000-0000-0000-0000-000000000102"));
        ReflectionTestUtils.setField(user, "nombre", "Profesor Demo");
        ReflectionTestUtils.setField(user, "email", email);
        ReflectionTestUtils.setField(user, "passwordHash", passwordHash);
        ReflectionTestUtils.setField(user, "activo", active);
        ReflectionTestUtils.setField(user, "roles", roles);
        return user;
    }

    private Role buildRole(String dbRoleName) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(role, "nombre", dbRoleName);
        return role;
    }
}
