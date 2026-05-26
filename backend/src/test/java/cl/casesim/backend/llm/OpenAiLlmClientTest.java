package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiLlmClientTest {

    @Test
    void extractOpenAiResponsesContentUsaOutputTextDirecto() {
        LlmProperties properties = new LlmProperties();
        OpenAiLlmClient client = new OpenAiLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        String content = client.extractOpenAiResponsesContent(Map.of("output_text", " Hola desde Responses API "));

        assertEquals("Hola desde Responses API", content);
    }

    @Test
    void extractOpenAiResponsesContentConcatenaBloquesDeOutput() {
        LlmProperties properties = new LlmProperties();
        OpenAiLlmClient client = new OpenAiLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        Map<String, Object> response = Map.of(
                "output", List.of(
                        Map.of(
                                "type", "message",
                                "content", List.of(
                                        Map.of("type", "output_text", "text", "Primera línea"),
                                        Map.of("type", "output_text", "text", "Segunda línea")
                                )
                        )
                )
        );

        String content = client.extractOpenAiResponsesContent(response);

        assertEquals("Primera línea\nSegunda línea", content);
    }

    @Test
    void buildOpenRouterPayloadParaClaudeIncluyeMaxCompletionTokens() {
        LlmProperties properties = new LlmProperties();
        properties.setModel("anthropic/claude-3.5-sonnet");
        OpenAiLlmClient client = new OpenAiLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        Map<String, Object> payload = client.buildOpenRouterPayload(List.of(new LlmMessage("user", "ping")), null, null);

        assertTrue(payload.containsKey("max_tokens"));
        assertTrue(payload.containsKey("max_completion_tokens"));
        assertEquals(payload.get("max_tokens"), payload.get("max_completion_tokens"));
        assertEquals("anthropic/claude-sonnet-4.5", payload.get("model"));
    }

    @Test
    void buildOpenRouterPayloadNormalizaAliasClaudeSinProvider() {
        LlmProperties properties = new LlmProperties();
        properties.setModel("claude-3.5-sonnet");
        OpenAiLlmClient client = new OpenAiLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        Map<String, Object> payload = client.buildOpenRouterPayload(List.of(new LlmMessage("user", "ping")), null, null);

        assertEquals("anthropic/claude-sonnet-4.5", payload.get("model"));
    }

    @Test
    void buildOpenRouterPayloadParaNoClaudeNoIncluyeMaxCompletionTokens() {
        LlmProperties properties = new LlmProperties();
        properties.setModel("openai/gpt-4.1-mini");
        OpenAiLlmClient client = new OpenAiLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        Map<String, Object> payload = client.buildOpenRouterPayload(List.of(new LlmMessage("user", "ping")), null, null);

        assertTrue(payload.containsKey("max_tokens"));
        assertFalse(payload.containsKey("max_completion_tokens"));
    }
}
