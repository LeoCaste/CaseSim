package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.BootstrapAdminRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthBootstrapServiceTest {

    private final PlatformSetupStateRepository stateRepository = mock(PlatformSetupStateRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void bootstrapStatusShouldBeFalseWhenActiveAdminExistsWithoutState() {
        AuthBootstrapService service = buildService("boot-token");
        when(stateRepository.findById(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.empty());
        when(userRepository.existsActiveAdmin()).thenReturn(true);

        boolean needsSetup = service.needsInitialSetup();

        assertFalse(needsSetup);
        verify(stateRepository).save(any(PlatformSetupState.class));
    }

    @Test
    void bootstrapStatusShouldBeTrueWhenNoStateAndNoAdmin() {
        AuthBootstrapService service = buildService("boot-token");
        when(stateRepository.findById(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.empty());
        when(userRepository.existsActiveAdmin()).thenReturn(false);

        assertTrue(service.needsInitialSetup());
        verify(stateRepository, never()).save(any(PlatformSetupState.class));
    }

    @Test
    void bootstrapAdminShouldBeBlockedWhenAlreadyInitialized() {
        AuthBootstrapService service = buildService("boot-token");
        PlatformSetupState initialized = new PlatformSetupState(1L, true, null, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(stateRepository.findById(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.of(initialized));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.bootstrapAdmin(request(), "boot-token"));

        assertTrue(ex.getReason().contains("inicializada"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrapAdminShouldBeBlockedWhenActiveAdminExistsWithoutState() {
        AuthBootstrapService service = buildService("boot-token");
        when(stateRepository.findById(PlatformSetupState.SINGLETON_ID)).thenReturn(Optional.empty());
        when(userRepository.existsActiveAdmin()).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.bootstrapAdmin(request(), null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("inicializada"));
        verify(stateRepository).save(any(PlatformSetupState.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrapAdminShouldFailWithInvalidToken() {
        AuthBootstrapService service = buildService("boot-token");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.bootstrapAdmin(request(), "bad"));

        assertTrue(ex.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN));
    }

    private AuthBootstrapService buildService(String bootstrapToken) {
        return new AuthBootstrapService(stateRepository, userRepository, roleRepository, institutionRepository, passwordEncoder, bootstrapToken);
    }

    private BootstrapAdminRequest request() {
        return new BootstrapAdminRequest("CaseSim", "Admin", "admin@ufrontera.cl", "Password1", "Password1");
    }
}
