package cl.casesim.backend.adminusers;

import cl.casesim.backend.adminusers.dto.AdminUserResponse;
import cl.casesim.backend.adminusers.dto.CreateAdminUserRequest;
import cl.casesim.backend.adminusers.dto.UpdateAdminUserRequest;
import cl.casesim.backend.adminusers.dto.UpdateAdminUserStatusRequest;
import cl.casesim.backend.auth.AppUser;
import cl.casesim.backend.auth.Role;
import cl.casesim.backend.auth.RoleRepository;
import cl.casesim.backend.auth.UserRepository;
import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ConflictException;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsersServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final AdminUsersService service = new AdminUsersService(userRepository, roleRepository, passwordEncoder);

    @Test
    void createUserShouldSetActiveTrueByDefault() {
        Role studentRole = role("ESTUDIANTE");
        when(userRepository.existsByEmailIgnoreCase("nuevo@ufromail.cl")).thenReturn(false);
        when(roleRepository.findByNombreIgnoreCase("ESTUDIANTE")).thenReturn(Optional.of(studentRole));
        when(passwordEncoder.encode(any())).thenReturn("dummy-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = service.createUser(new CreateAdminUserRequest(
                "Nuevo Usuario",
                "nuevo@ufromail.cl",
                "ESTUDIANTE",
                null
        ));

        assertTrue(response.active());
        assertEquals(Set.of("ESTUDIANTE"), response.roles());
    }

    @Test
    void getUsersShouldReturnOnlyActiveByDefault() {
        AppUser activeUser = new AppUser(UUID.randomUUID(), "Activo", "activo@ufromail.cl", "hash", true, Set.of(role("ESTUDIANTE")));
        AppUser inactiveUser = new AppUser(UUID.randomUUID(), "Inactivo", "inactivo@ufromail.cl", "hash", false, Set.of(role("PROFESOR")));
        when(userRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(activeUser, inactiveUser));

        List<AdminUserResponse> responses = service.getUsers(null);

        assertEquals(1, responses.size());
        assertTrue(responses.getFirst().active());
        assertEquals("Activo", responses.getFirst().name());
    }

    @Test
    void getUsersShouldReturnOnlyInactiveWhenActiveFalse() {
        AppUser activeUser = new AppUser(UUID.randomUUID(), "Activo", "activo@ufromail.cl", "hash", true, Set.of(role("ESTUDIANTE")));
        AppUser inactiveUser = new AppUser(UUID.randomUUID(), "Inactivo", "inactivo@ufromail.cl", "hash", false, Set.of(role("PROFESOR")));
        when(userRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(activeUser, inactiveUser));

        List<AdminUserResponse> responses = service.getUsers("false");

        assertEquals(1, responses.size());
        assertFalse(responses.getFirst().active());
        assertEquals("Inactivo", responses.getFirst().name());
    }

    @Test
    void getUsersShouldReturnAllWhenActiveAll() {
        AppUser activeUser = new AppUser(UUID.randomUUID(), "Activo", "activo@ufromail.cl", "hash", true, Set.of(role("ESTUDIANTE")));
        AppUser inactiveUser = new AppUser(UUID.randomUUID(), "Inactivo", "inactivo@ufromail.cl", "hash", false, Set.of(role("PROFESOR")));
        when(userRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(activeUser, inactiveUser));

        List<AdminUserResponse> responses = service.getUsers("all");

        assertEquals(2, responses.size());
    }

    @Test
    void getUsersShouldFailWhenActiveFilterIsInvalid() {
        assertThrows(BadRequestException.class, () -> service.getUsers("invalid"));
        verify(userRepository, never()).findAllByOrderByNombreAsc();
    }

    @Test
    void createUserShouldFailWhenEmailAlreadyExists() {
        when(userRepository.existsByEmailIgnoreCase("existe@ufromail.cl")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createUser(new CreateAdminUserRequest(
                "Usuario",
                "existe@ufromail.cl",
                "PROFESOR",
                null
        )));
    }

    @Test
    void createUserShouldRequirePasswordForAdminRole() {
        Role adminRole = role("ADMIN");
        when(userRepository.existsByEmailIgnoreCase("admin@ufrontera.cl")).thenReturn(false);
        when(roleRepository.findByNombreIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));

        assertThrows(BadRequestException.class, () -> service.createUser(new CreateAdminUserRequest(
                "Admin",
                "admin@ufrontera.cl",
                "ADMIN",
                null
        )));
    }

    @Test
    void createUserShouldEncodePasswordWhenProvided() {
        Role adminRole = role("ADMIN");
        when(userRepository.existsByEmailIgnoreCase("admin@ufrontera.cl")).thenReturn(false);
        when(roleRepository.findByNombreIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("admin123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = service.createUser(new CreateAdminUserRequest(
                "Admin",
                "admin@ufrontera.cl",
                "ADMIN",
                "admin123"
        ));

        assertEquals(Set.of("ADMIN"), response.roles());
        verify(passwordEncoder).encode("admin123");
    }

    @Test
    void updateUserShouldRequirePasswordWhenChangingToAdmin() {
        UUID userId = UUID.randomUUID();
        AppUser existing = new AppUser(userId, "Profe", "profe@ufromail.cl", "", true, Set.of(role("PROFESOR")));
        Role adminRole = role("ADMIN");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("profe@ufromail.cl", userId)).thenReturn(false);
        when(roleRepository.findByNombreIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));

        assertThrows(BadRequestException.class, () -> service.updateUser(userId, new UpdateAdminUserRequest(
                "Profe",
                "profe@ufromail.cl",
                "ADMIN",
                null
        )));
    }

    @Test
    void updateUserShouldUpdatePasswordHashWhenPasswordArrives() {
        UUID userId = UUID.randomUUID();
        AppUser existing = new AppUser(userId, "Admin", "admin@ufrontera.cl", "old-hash", true, Set.of(role("ADMIN")));
        Role adminRole = role("ADMIN");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("admin@ufrontera.cl", userId)).thenReturn(false);
        when(roleRepository.findByNombreIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateUser(userId, new UpdateAdminUserRequest(
                "Admin",
                "admin@ufrontera.cl",
                "ADMIN",
                "new-secret"
        ));

        assertEquals("new-hash", existing.getPasswordHash());
    }

    @Test
    void updateUserShouldKeepPasswordHashWhenPasswordDoesNotArrive() {
        UUID userId = UUID.randomUUID();
        AppUser existing = new AppUser(userId, "Admin", "admin@ufrontera.cl", "old-hash", true, Set.of(role("ADMIN")));
        Role adminRole = role("ADMIN");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("admin@ufrontera.cl", userId)).thenReturn(false);
        when(roleRepository.findByNombreIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateUser(userId, new UpdateAdminUserRequest(
                "Admin",
                "admin@ufrontera.cl",
                "ADMIN",
                null
        ));

        assertEquals("old-hash", existing.getPasswordHash());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void patchStatusShouldOnlyChangeActiveState() {
        UUID userId = UUID.randomUUID();
        AppUser existing = new AppUser(userId, "Est", "est@ufromail.cl", "", true, Set.of(role("ESTUDIANTE")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = service.updateUserStatus(userId, new UpdateAdminUserStatusRequest(false));

        assertFalse(response.active());
        assertEquals(Set.of("ESTUDIANTE"), response.roles());
    }

    @Test
    void deleteUserShouldPhysicallyDeleteWhenUserExists() {
        UUID userId = UUID.randomUUID();
        AppUser existing = new AppUser(userId, "Usuario", "usuario@ufromail.cl", "hash", true, Set.of(role("ESTUDIANTE")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        service.deleteUser(userId);

        verify(userRepository).delete(existing);
        verify(userRepository).flush();
    }

    @Test
    void deleteUserShouldThrowWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteUser(userId));
        verify(userRepository, never()).delete(any(AppUser.class));
    }

    @Test
    void deleteUserShouldThrowConflictWhenUserHasAssociatedData() {
        UUID userId = UUID.randomUUID();
        AppUser existing = new AppUser(userId, "Usuario", "usuario@ufromail.cl", "hash", true, Set.of(role("ESTUDIANTE")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        doThrow(new DataIntegrityViolationException("FK constraint")).when(userRepository).flush();

        ConflictException exception = assertThrows(ConflictException.class, () -> service.deleteUser(userId));

        assertEquals("No se puede eliminar el usuario porque tiene datos asociados.", exception.getMessage());
        verify(userRepository).delete(existing);
        verify(userRepository).flush();
    }

    private Role role(String name) {
        return new Role(UUID.randomUUID(), name);
    }
}
