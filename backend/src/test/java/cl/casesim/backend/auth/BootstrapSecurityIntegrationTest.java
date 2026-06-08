package cl.casesim.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BootstrapSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthBootstrapService authBootstrapService;

    @Test
    void bootstrapEndpointsShouldBePublic() throws Exception {
        when(authBootstrapService.adminExists()).thenReturn(false);

        mockMvc.perform(get("/api/v1/bootstrap/admin/status"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bootstrap/admin")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "admin@ufrontera.cl",
                                  "password": "Password1",
                                  "confirmPassword": "Password1"
                                }
                                """))
                .andExpect(status().isNoContent());
    }
}
