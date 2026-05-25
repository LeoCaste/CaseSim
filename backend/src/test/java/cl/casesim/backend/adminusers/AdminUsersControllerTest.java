package cl.casesim.backend.adminusers;

import cl.casesim.backend.adminusers.dto.AdminUserResponse;
import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ConflictException;
import cl.casesim.backend.common.exception.GlobalExceptionHandler;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUsersControllerTest {

    private final AdminUsersService adminUsersService = mock(AdminUsersService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminUsersController controller = new AdminUsersController(adminUsersService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getUsersShouldUseActiveTrueByDefault() throws Exception {
        AdminUserResponse activeUser = new AdminUserResponse(UUID.randomUUID(), "Activo", "activo@ufromail.cl", true, Set.of("ADMIN"));
        when(adminUsersService.getUsers("true")).thenReturn(List.of(activeUser));

        mockMvc.perform(get("/api/v1/admin/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Activo"))
                .andExpect(jsonPath("$[0].active").value(true));

        verify(adminUsersService).getUsers("true");
    }

    @Test
    void getUsersShouldSupportActiveFalse() throws Exception {
        AdminUserResponse inactiveUser = new AdminUserResponse(UUID.randomUUID(), "Inactivo", "inactivo@ufromail.cl", false, Set.of("PROFESOR"));
        when(adminUsersService.getUsers("false")).thenReturn(List.of(inactiveUser));

        mockMvc.perform(get("/api/v1/admin/users").param("active", "false").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Inactivo"))
                .andExpect(jsonPath("$[0].active").value(false));

        verify(adminUsersService).getUsers("false");
    }

    @Test
    void getUsersShouldSupportActiveAll() throws Exception {
        AdminUserResponse activeUser = new AdminUserResponse(UUID.randomUUID(), "Activo", "activo@ufromail.cl", true, Set.of("ADMIN"));
        AdminUserResponse inactiveUser = new AdminUserResponse(UUID.randomUUID(), "Inactivo", "inactivo@ufromail.cl", false, Set.of("PROFESOR"));
        when(adminUsersService.getUsers("all")).thenReturn(List.of(activeUser, inactiveUser));

        mockMvc.perform(get("/api/v1/admin/users").param("active", "all").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(adminUsersService).getUsers("all");
    }

    @Test
    void getUsersShouldReturnBadRequestForInvalidActiveValue() throws Exception {
        when(adminUsersService.getUsers("invalid")).thenThrow(new BadRequestException("El parámetro active debe ser true, false o all."));

        mockMvc.perform(get("/api/v1/admin/users").param("active", "invalid").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro active debe ser true, false o all."));

        verify(adminUsersService).getUsers("invalid");
    }

    @Test
    void deleteUserShouldReturnNoContentWhenDeletionSucceeds() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId))
                .andExpect(status().isNoContent());

        verify(adminUsersService).deleteUser(userId);
    }

    @Test
    void deleteUserShouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Usuario no encontrado con id: " + userId))
                .when(adminUsersService).deleteUser(userId);

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuario no encontrado con id: " + userId));

        verify(adminUsersService).deleteUser(userId);
    }

    @Test
    void deleteUserShouldReturnConflictWhenUserHasAssociatedData() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new ConflictException("No se puede eliminar el usuario porque tiene datos asociados."))
                .when(adminUsersService).deleteUser(userId);

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede eliminar el usuario porque tiene datos asociados."));

        verify(adminUsersService).deleteUser(userId);
    }
}
