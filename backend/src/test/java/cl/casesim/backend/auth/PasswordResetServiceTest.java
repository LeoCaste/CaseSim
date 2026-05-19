package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.ForgotPasswordRequest;
import cl.casesim.backend.auth.dto.ResetPasswordRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PasswordResetService service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder);

    @Test
    void forgotPasswordShouldPersistTokenForActiveAdmin() {
        AppUser admin = buildUser(true, Set.of(buildRole("ADMIN")));
        when(userRepository.findByEmailIgnoreCaseAndActivoTrue("admin@ufrontera.cl")).thenReturn(Optional.of(admin));

        service.requestReset(new ForgotPasswordRequest("admin@ufrontera.cl"));

        verify(tokenRepository).invalidateActiveTokens(eq(admin.getId()), any(LocalDateTime.class));
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void forgotPasswordShouldBeNeutralWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCaseAndActivoTrue("missing@ufrontera.cl")).thenReturn(Optional.empty());

        service.requestReset(new ForgotPasswordRequest("missing@ufrontera.cl"));

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordShouldUpdatePasswordWhenTokenIsValid() {
        AppUser admin = buildUser(true, Set.of(buildRole("ADMIN")));
        PasswordResetToken token = new PasswordResetToken(UUID.randomUUID(), admin, "hash", LocalDateTime.now().plusMinutes(10), null, LocalDateTime.now());
        when(tokenRepository.findValidByTokenHash(any(), any(LocalDateTime.class))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("encoded");

        service.resetPassword(new ResetPasswordRequest("raw-token", "NewPassword1", "NewPassword1"));

        assertEquals("encoded", admin.getPasswordHash());
        verify(tokenRepository).invalidateActiveTokens(eq(admin.getId()), any(LocalDateTime.class));
    }

    @Test
    void resetPasswordShouldFailWhenTokenIsInvalid() {
        when(tokenRepository.findValidByTokenHash(any(), any(LocalDateTime.class))).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword(new ResetPasswordRequest("bad", "NewPassword1", "NewPassword1")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    private AppUser buildUser(boolean active, Set<Role> roles) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", UUID.fromString("00000000-0000-0000-0000-000000000102"));
        ReflectionTestUtils.setField(user, "nombre", "Admin Demo");
        ReflectionTestUtils.setField(user, "email", "admin@ufrontera.cl");
        ReflectionTestUtils.setField(user, "passwordHash", "old-hash");
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
