package cl.casesim.backend.llm;

import cl.casesim.backend.common.exception.GlobalExceptionHandler;
import cl.casesim.backend.llm.dto.TestConnectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LlmAdminControllerTest {

    private final LlmAdminService llmAdminService = mock(LlmAdminService.class);
    private final LlmUsageService llmUsageService = mock(LlmUsageService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LlmAdminController controller = new LlmAdminController(llmAdminService, llmUsageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testConnectionShouldReturn200WhenSuccess() throws Exception {
        when(llmAdminService.testConnection()).thenReturn(new TestConnectionResponse(
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
        ));

        mockMvc.perform(post("/api/v1/admin/llm/test-connection").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.httpStatus").value(200));

        verify(llmAdminService).testConnection();
    }

    @Test
    void testConnectionShouldReturnMappedStatusWhenProviderFails() throws Exception {
        when(llmAdminService.testConnection()).thenReturn(new TestConnectionResponse(
                false,
                401,
                "API key inválida o no autorizada (401).",
                "API key inválida o no autorizada (401).",
                401,
                "AUTH_ERROR",
                "openrouter",
                "anthropic/claude-3.5-sonnet",
                "/api/v1/chat/completions",
                "openrouter.ai",
                "trace_abc123",
                "request-id=trace_abc123"
        ));

        mockMvc.perform(post("/api/v1/admin/llm/test-connection").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AUTH_ERROR"))
                .andExpect(jsonPath("$.httpStatus").value(401));

        verify(llmAdminService).testConnection();
    }

    @Test
    void testConnectionShouldSupportCompatibilityPaths() throws Exception {
        when(llmAdminService.testConnection()).thenReturn(new TestConnectionResponse(
                true,
                200,
                "Conexión exitosa.",
                "Conexión exitosa.",
                200,
                null,
                "anthropic",
                "anthropic/claude-3.5-sonnet",
                "/api/v1/chat/completions",
                "openrouter.ai",
                null,
                null
        ));

        mockMvc.perform(post("/api/v1/admin/llm/testConnection").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/admin/llm/test-connection/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200));
    }
}
