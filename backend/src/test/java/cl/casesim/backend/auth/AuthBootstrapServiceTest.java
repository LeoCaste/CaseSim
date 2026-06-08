package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.BootstrapAdminRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthBootstrapServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PlatformSetupStateRepository platformSetupStateRepository = mock(PlatformSetupStateRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void adminExistsShouldBeTrueWhenAnyAdminExists() {
        AuthBootstrapService service = buildService();
        when(userRepository.existsAdmin()).thenReturn(true);

        assertTrue(service.adminExists());
    }

    @Test
    void needsInitialSetupShouldBeTrueWhenNoAdminExists() {
        AuthBootstrapService service = buildService();
        when(userRepository.existsAdmin()).thenReturn(false);

        assertTrue(service.needsInitialSetup());
    }

    @Test
    void bootstrapAdminShouldBeBlockedWhenAdminAlreadyExists() {
        AuthBootstrapService service = buildService();
        PlatformSetupState state = new PlatformSetupState(PlatformSetupState.SINGLETON_ID, false, null, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(platformSetupStateRepository.findByIdForUpdate(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.of(state));
        when(userRepository.existsAdmin()).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.bootstrapAdmin(request()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("bootstrap inicial está bloqueado"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrapAdminShouldCreateFirstAdminWithEncodedPassword() {
        AuthBootstrapService service = buildService();
        Role adminRole = buildRole("ADMIN");
        PlatformSetupState state = new PlatformSetupState(PlatformSetupState.SINGLETON_ID, false, null, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(platformSetupStateRepository.findByIdForUpdate(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.of(state));
        when(userRepository.existsAdmin()).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("admin@ufrontera.cl")).thenReturn(false);
        when(roleRepository.findByNombreIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("Password1")).thenReturn("$2a$10$hashed");

        service.bootstrapAdmin(request());

        verify(userRepository).save(argThat(user -> {
            if (!"Administrador".equals(user.getNombre())) {
                return false;
            }
            if (!"admin@ufrontera.cl".equals(user.getEmail())) {
                return false;
            }
            if (!"$2a$10$hashed".equals(user.getPasswordHash())) {
                return false;
            }
            return user.isActivo() && user.getRoles().equals(Set.of(adminRole));
        }));
        verify(platformSetupStateRepository).save(argThat(updated -> updated.isInitialized()));
        verify(passwordEncoder).encode("Password1");
    }

    @Test
    void bootstrapAdminShouldFailWhenEmailAlreadyExists() {
        AuthBootstrapService service = buildService();
        PlatformSetupState state = new PlatformSetupState(PlatformSetupState.SINGLETON_ID, false, null, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(platformSetupStateRepository.findByIdForUpdate(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.of(state));
        when(userRepository.existsAdmin()).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("admin@ufrontera.cl")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.bootstrapAdmin(request()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("email del administrador ya existe"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrapAdminShouldFailWhenSetupStateIsAlreadyInitialized() {
        AuthBootstrapService service = buildService();
        PlatformSetupState state = new PlatformSetupState(PlatformSetupState.SINGLETON_ID, true, java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(platformSetupStateRepository.findByIdForUpdate(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.of(state));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.bootstrapAdmin(request()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("bootstrap inicial está bloqueado"));
        verify(userRepository, never()).save(any());
    }

    private AuthBootstrapService buildService() {
        return new AuthBootstrapService(userRepository, roleRepository, platformSetupStateRepository, passwordEncoder);
    }

    private BootstrapAdminRequest request() {
        return new BootstrapAdminRequest("admin@ufrontera.cl", "Password1", "Password1");
    }

    private Role buildRole(String dbRoleName) {
        Role role = new Role();
        org.springframework.test.util.ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(role, "nombre", dbRoleName);
        return role;
    }
}
