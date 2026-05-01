package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqLlmClientTest {

    @Test
    void buildGroqPayloadMapeaCamposBasicos() {
        LlmProperties properties = new LlmProperties();
        properties.setModel("llama-3.1-8b-instant");
        properties.setTemperature(0.2);
        properties.setMaxTokens(120);

        GroqLlmClient client = new GroqLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        Map<String, Object> payload = client.buildGroqPayload(
                List.of(new LlmMessage("user", "hola")),
                null,
                null
        );

        assertEquals("llama-3.1-8b-instant", payload.get("model"));
        assertEquals(0.2, payload.get("temperature"));
        assertEquals(120, payload.get("max_tokens"));
        assertTrue(payload.containsKey("messages"));
    }

    @Test
    void extractGroqContentExtraeMensajeChoice() {
        LlmProperties properties = new LlmProperties();
        GroqLlmClient client = new GroqLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        String content = client.extractGroqContent(Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", " respuesta groq "))
                )
        ));

        assertEquals("respuesta groq", content);
    }

    @Test
    void mapHttpErrorMessageClasificaQuotaComoProviderUnavailable() {
        LlmProperties properties = new LlmProperties();
        GroqLlmClient client = new GroqLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        String message = client.mapHttpErrorMessage(429, "insufficient_quota", "/openai/v1/chat/completions");

        assertTrue(message.contains("category=QUOTA_EXCEEDED"));
        assertTrue(message.contains("status=429"));
    }

    @Test
    void resolveGroqUrlConBaseDefaultUsaHostGroqYPathUnico() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("groq");
        properties.setBaseUrl("https://api.groq.com");

        GroqLlmClient client = new GroqLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());
        String resolvedUrl = client.resolveGroqUrl();

        assertEquals("https://api.groq.com/openai/v1/chat/completions", resolvedUrl);
        assertEquals("/openai/v1/chat/completions", client.resolveRequestPath(resolvedUrl));
    }

    @Test
    void resolveGroqUrlConBaseOpenAiAccidentalHaceFallbackAGroq() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("groq");
        properties.setBaseUrl("https://api.openai.com/v1/chat/completions");

        GroqLlmClient client = new GroqLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());
        String resolvedUrl = client.resolveGroqUrl();

        assertEquals("https://api.groq.com/openai/v1/chat/completions", resolvedUrl);
    }

    @Test
    void mapHttpErrorMessageClasificaAuthError401() {
        LlmProperties properties = new LlmProperties();
        GroqLlmClient client = new GroqLlmClient(properties, new LlmProviderUrlResolver(), new LlmProviderErrorMapper());

        String message = client.mapHttpErrorMessage(401, "invalid api key", "/openai/v1/chat/completions");

        assertTrue(message.contains("category=AUTH_ERROR"));
        assertTrue(message.contains("status=401"));
    }
}
