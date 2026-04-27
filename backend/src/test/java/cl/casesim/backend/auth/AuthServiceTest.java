package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.LoginRequest;
import cl.casesim.backend.auth.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService authService = new AuthService(userRepository, jwtService);

    @Test
    void loginShouldReturnTokenWhenUserIsActiveAndHasRoles() {
        AppUser user = buildUser(true, Set.of(buildRole("ESTUDIANTE")));
        when(userRepository.findByEmailIgnoreCase("estudiante.demo01@ufromail.cl")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("estudiante.demo01@ufromail.cl"));

        assertEquals("jwt-token", response.token());
        assertEquals(user.getId(), response.user().id());
        assertEquals(user.getNombre(), response.user().name());
        assertEquals(user.getEmail(), response.user().email());
        assertEquals(Set.of("ESTUDIANTE"), response.user().roles());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("missing@ufromail.cl")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("missing@ufromail.cl"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenUserIsInactive() {
        AppUser user = buildUser(false, Set.of(buildRole("ESTUDIANTE")));
        when(userRepository.findByEmailIgnoreCase("estudiante.demo01@ufromail.cl")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("estudiante.demo01@ufromail.cl"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenUserHasNoRoles() {
        AppUser user = buildUser(true, Set.of());
        when(userRepository.findByEmailIgnoreCase("estudiante.demo01@ufromail.cl")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("estudiante.demo01@ufromail.cl"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void loginShouldReturnUnauthorizedWhenEmailIsNotInstitutional() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo@gmail.com"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    private AppUser buildUser(boolean active, Set<Role> roles) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", UUID.fromString("00000000-0000-0000-0000-000000000102"));
        ReflectionTestUtils.setField(user, "nombre", "Estudiante Demo 01");
        ReflectionTestUtils.setField(user, "email", "estudiante.demo01@ufromail.cl");
        ReflectionTestUtils.setField(user, "passwordHash", "unused");
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
