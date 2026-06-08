package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.LoginRequest;
import cl.casesim.backend.auth.dto.LoginResponse;
import cl.casesim.backend.auth.dto.PreCheckRequest;
import cl.casesim.backend.auth.dto.PreCheckResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthService authService = new AuthService(userRepository, jwtService, passwordEncoder);

    @Test
    void loginShouldReturnTokenWhenNonAdminUserIsActiveAndHasRoles() {
        AppUser user = buildUser(true, "estudiante.demo01@ufromail.cl", "unused", Set.of(buildRole("ESTUDIANTE")));
        when(userRepository.findByEmailIgnoreCase("estudiante.demo01@ufromail.cl")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("estudiante.demo01@ufromail.cl", null));

        assertEquals("jwt-token", response.token());
        assertEquals(user.getId(), response.user().id());
        assertEquals(user.getNombre(), response.user().name());
        assertEquals(user.getEmail(), response.user().email());
        assertEquals(Set.of("ESTUDIANTE"), response.user().roles());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("missing@ufromail.cl")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("missing@ufromail.cl", null))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenUserIsInactive() {
        AppUser user = buildUser(false, "estudiante.demo01@ufromail.cl", "unused", Set.of(buildRole("ESTUDIANTE")));
        when(userRepository.findByEmailIgnoreCase("estudiante.demo01@ufromail.cl")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("estudiante.demo01@ufromail.cl", null))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenUserHasNoRoles() {
        AppUser user = buildUser(true, "estudiante.demo01@ufromail.cl", "unused", Set.of());
        when(userRepository.findByEmailIgnoreCase("estudiante.demo01@ufromail.cl")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("estudiante.demo01@ufromail.cl", null))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenEmailIsNotInstitutional() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo@gmail.com", null))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenAdminDoesNotSendPassword() {
        AppUser user = buildUser(true, "admin.demo@ufrontera.cl", "$2a$10$adminhash", Set.of(buildRole("ADMIN")));
        when(userRepository.findByEmailIgnoreCase("admin.demo@ufrontera.cl")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("admin.demo@ufrontera.cl", null))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenAdminPasswordIsInvalid() {
        AppUser user = buildUser(true, "admin.demo@ufrontera.cl", "$2a$10$adminhash", Set.of(buildRole("ADMIN")));
        when(userRepository.findByEmailIgnoreCase("admin.demo@ufrontera.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-password", "$2a$10$adminhash")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("admin.demo@ufrontera.cl", "bad-password"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnTokenWhenAdminPasswordIsValid() {
        AppUser user = buildUser(true, "admin.demo@ufrontera.cl", "$2a$10$adminhash", Set.of(buildRole("ADMIN")));
        when(userRepository.findByEmailIgnoreCase("admin.demo@ufrontera.cl")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("good-password", "$2a$10$adminhash")).thenReturn(true);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-admin-token");

        LoginResponse response = authService.login(new LoginRequest("admin.demo@ufrontera.cl", "good-password"));

        assertEquals("jwt-admin-token", response.token());
        verify(passwordEncoder).matches(eq("good-password"), eq("$2a$10$adminhash"));
    }


    @Test
    void loginShouldAllowAdminWithNonInstitutionalEmailWhenPasswordIsValid() {
        AppUser user = buildUser(true, "admin.personal@gmail.com", "$2a$10$adminhash", Set.of(buildRole("ADMIN")));
        when(userRepository.findByEmailIgnoreCase("admin.personal@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("good-password", "$2a$10$adminhash")).thenReturn(true);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-admin-token");

        LoginResponse response = authService.login(new LoginRequest("admin.personal@gmail.com", "good-password"));

        assertEquals("jwt-admin-token", response.token());
        verify(passwordEncoder).matches(eq("good-password"), eq("$2a$10$adminhash"));
    }

    @Test
    void preCheckShouldReturnTrueForActiveAdmin() {
        AppUser user = buildUser(true, "admin.demo@ufrontera.cl", "$2a$10$adminhash", Set.of(buildRole("ADMIN")));
        when(userRepository.findByEmailIgnoreCase("admin.demo@ufrontera.cl")).thenReturn(Optional.of(user));

        PreCheckResponse response = authService.preCheck(new PreCheckRequest("admin.demo@ufrontera.cl"));

        assertTrue(response.requiresPassword());
    }

    @Test
    void preCheckShouldReturnFalseWhenUserIsNotAdminOrDoesNotExist() {
        AppUser student = buildUser(true, "estudiante.demo01@ufromail.cl", "unused", Set.of(buildRole("ESTUDIANTE")));
        when(userRepository.findByEmailIgnoreCase("estudiante.demo01@ufromail.cl")).thenReturn(Optional.of(student));
        when(userRepository.findByEmailIgnoreCase("missing@ufromail.cl")).thenReturn(Optional.empty());

        PreCheckResponse studentResponse = authService.preCheck(new PreCheckRequest("estudiante.demo01@ufromail.cl"));
        PreCheckResponse missingResponse = authService.preCheck(new PreCheckRequest("missing@ufromail.cl"));

        assertFalse(studentResponse.requiresPassword());
        assertFalse(missingResponse.requiresPassword());
    }

    @Test
    void preCheckShouldReturnFalseForInvalidEmailFormat() {
        PreCheckResponse response = authService.preCheck(new PreCheckRequest("demo@gmail.com"));

        assertFalse(response.requiresPassword());
    }

    private AppUser buildUser(boolean active, String email, String passwordHash, Set<Role> roles) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", UUID.fromString("00000000-0000-0000-0000-000000000102"));
        ReflectionTestUtils.setField(user, "nombre", "Usuario Demo");
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
