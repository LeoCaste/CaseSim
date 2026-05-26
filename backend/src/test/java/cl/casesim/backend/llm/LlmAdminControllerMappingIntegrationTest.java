package cl.casesim.backend.llm;

import cl.casesim.backend.llm.dto.TestConnectionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LlmAdminControllerMappingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmAdminService llmAdminService;

    @MockitoBean
    private LlmUsageService llmUsageService;

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testConnectionShouldMapKebabCasePath() throws Exception {
        when(llmAdminService.testConnection()).thenReturn(successResponse());

        mockMvc.perform(post("/api/v1/admin/llm/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.provider").value("openrouter"));

        verify(llmAdminService).testConnection();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testConnectionShouldMapCamelCasePath() throws Exception {
        when(llmAdminService.testConnection()).thenReturn(successResponse());

        mockMvc.perform(post("/api/v1/admin/llm/testConnection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.model").value("anthropic/claude-3.5-sonnet"));

        verify(llmAdminService).testConnection();
    }

    private TestConnectionResponse successResponse() {
        return new TestConnectionResponse(
                true,
                200,
                "Conexión exitosa.",
                "Conexión exitosa.",
                200,
                null,
                "openrouter",
                "anthropic/claude-3.5-sonnet",
                "/api/v1/chat/completions",
                "openrouter.ai",
                null,
                null
        );
    }
}
