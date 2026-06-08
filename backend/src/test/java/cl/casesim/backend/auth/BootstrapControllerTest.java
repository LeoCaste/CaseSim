package cl.casesim.backend.auth;

import cl.casesim.backend.auth.dto.BootstrapAdminRequest;
import cl.casesim.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BootstrapControllerTest {

    private final AuthBootstrapService authBootstrapService = mock(AuthBootstrapService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BootstrapController controller = new BootstrapController(authBootstrapService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void statusShouldExposeWhetherAnAdminExists() throws Exception {
        when(authBootstrapService.adminExists()).thenReturn(true);

        mockMvc.perform(get("/api/v1/bootstrap/admin/status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminExists").value(true));

        verify(authBootstrapService).adminExists();
    }

    @Test
    void bootstrapAdminShouldReturnNoContentWhenCreationSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/bootstrap/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@ufrontera.cl",
                                  "password": "Password1",
                                  "confirmPassword": "Password1"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(authBootstrapService).bootstrapAdmin(new BootstrapAdminRequest(
                "admin@ufrontera.cl",
                "Password1",
                "Password1"
        ));
    }

    @Test
    void bootstrapAdminShouldReturnConflictWhenAdminAlreadyExists() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Ya existe un administrador. El bootstrap inicial está bloqueado."))
                .when(authBootstrapService).bootstrapAdmin(org.mockito.ArgumentMatchers.any(BootstrapAdminRequest.class));

        mockMvc.perform(post("/api/v1/bootstrap/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@ufrontera.cl",
                                  "password": "Password1",
                                  "confirmPassword": "Password1"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un administrador. El bootstrap inicial está bloqueado."));

        verify(authBootstrapService).bootstrapAdmin(org.mockito.ArgumentMatchers.any(BootstrapAdminRequest.class));
    }
}
