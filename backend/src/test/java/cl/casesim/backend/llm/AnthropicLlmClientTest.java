package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnthropicLlmClientTest {

    @Test
    void buildAnthropicPayloadMapeaSystemYMensajesConversacionales() {
        LlmProperties properties = new LlmProperties();
        properties.setModel("claude-sonnet-4-5");
        AnthropicLlmClient client = new AnthropicLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        LlmRequest request = new LlmRequest(List.of(
                new LlmMessage("system", "Regla uno"),
                new LlmMessage("system", "Regla dos"),
                new LlmMessage("user", "Hola"),
                new LlmMessage("assistant", "¿Cómo estás?")
        ), null, 0.3, 256);

        Map<String, Object> payload = client.buildAnthropicPayload(request);

        assertEquals("claude-sonnet-4-5", payload.get("model"));
        assertEquals("Regla uno\n\nRegla dos", payload.get("system"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        assertEquals(2, messages.size());
        assertEquals("user", messages.getFirst().get("role"));
        assertEquals("Hola", messages.getFirst().get("content"));
    }

    @Test
    void extractAnthropicContentConcatenaBloquesDeTexto() {
        LlmProperties properties = new LlmProperties();
        AnthropicLlmClient client = new AnthropicLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        Map<String, Object> response = Map.of(
                "content", List.of(
                        Map.of("type", "text", "text", "Primera"),
                        Map.of("type", "text", "text", "Segunda")
                )
        );

        assertEquals("Primera\nSegunda", client.extractAnthropicContent(response));
    }

    @Test
    void extractAnthropicContentVacioSiNoHayBloquesDeTexto() {
        LlmProperties properties = new LlmProperties();
        AnthropicLlmClient client = new AnthropicLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        Map<String, Object> response = Map.of(
                "content", List.of(Map.of("type", "tool_use", "id", "tool_1"))
        );

        assertEquals("", client.extractAnthropicContent(response));
    }

    @Test
    void generateRechazaModeloConPrefijoAnthropic() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("anthropic");
        properties.setModel("anthropic/claude-sonnet-4.5");
        properties.setApiKey("sk-ant-test");
        AnthropicLlmClient client = new AnthropicLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.generate(new LlmRequest(List.of(new LlmMessage("user", "hola")), null, null, null)));
        assertEquals(LlmErrorCategory.INVALID_REQUEST, ex.providerError().category());
    }
}
