package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmClientRouterTest {

    @Test
    void seleccionaGroqCuandoProviderEsGroq() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("groq");

        RecordingClient defaultClient = new RecordingClient("openai", "default");
        RecordingClient groqClient = new RecordingClient("groq");

        LlmClientRouter router = new LlmClientRouter(properties, List.of(defaultClient, groqClient));

        String result = router.generateChatCompletion(List.of(new LlmMessage("user", "hola")), 0.4, 100);

        assertEquals("groq", result);
        assertEquals(0, defaultClient.calls);
        assertEquals(1, groqClient.calls);
    }

    @Test
    void seleccionaDefaultCuandoProviderNoEsGroq() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("openai");

        RecordingClient defaultClient = new RecordingClient("openai", "default");
        RecordingClient groqClient = new RecordingClient("groq");

        LlmClientRouter router = new LlmClientRouter(properties, List.of(defaultClient, groqClient));

        String result = router.generateChatCompletion(List.of(new LlmMessage("user", "hola")), 0.4, 100);

        assertEquals("default", result);
        assertEquals(1, defaultClient.calls);
        assertEquals(0, groqClient.calls);
    }

    @Test
    void providerDesconocidoFallaClaro() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("desconocido");
        LlmClientRouter router = new LlmClientRouter(properties, List.of(new RecordingClient("openai"), new RecordingClient("groq")));

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> router.generate(new LlmRequest(List.of(new LlmMessage("user", "hola")), "m", 0.1, 10)));
        assertEquals(LlmErrorCategory.INVALID_REQUEST, ex.providerError().category());
    }

    @Test
    void seleccionaGeminiCuandoProviderEsGemini() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("gemini");

        RecordingClient defaultClient = new RecordingClient("openai", "default");
        RecordingClient groqClient = new RecordingClient("groq");
        RecordingClient geminiClient = new RecordingClient("gemini");

        LlmClientRouter router = new LlmClientRouter(properties, List.of(defaultClient, groqClient, geminiClient));

        String result = router.generateChatCompletion(List.of(new LlmMessage("user", "hola")), 0.4, 100);

        assertEquals("gemini", result);
        assertEquals(0, defaultClient.calls);
        assertEquals(0, groqClient.calls);
        assertEquals(1, geminiClient.calls);
    }

    @Test
    void seleccionaClienteOpenAiCuandoProviderEsOpenRouter() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("openrouter");

        RecordingClient defaultClient = new RecordingClient("openai", "default");
        RecordingClient groqClient = new RecordingClient("groq");

        LlmClientRouter router = new LlmClientRouter(properties, List.of(defaultClient, groqClient));

        String result = router.generateChatCompletion(List.of(new LlmMessage("user", "hola")), 0.4, 100);

        assertEquals("default", result);
        assertEquals(1, defaultClient.calls);
        assertEquals(0, groqClient.calls);
    }

    @Test
    void seleccionaAnthropicCuandoProviderEsAnthropic() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("anthropic");

        RecordingClient defaultClient = new RecordingClient("openai", "default");
        RecordingClient anthropicClient = new RecordingClient("anthropic");

        LlmClientRouter router = new LlmClientRouter(properties, List.of(defaultClient, anthropicClient));

        String result = router.generateChatCompletion(List.of(new LlmMessage("user", "hola")), 0.4, 100);

        assertEquals("anthropic", result);
        assertEquals(0, defaultClient.calls);
        assertEquals(1, anthropicClient.calls);
    }

    private static class RecordingClient implements LlmClient {
        private final String provider;
        private final String response;
        private int calls = 0;

        private RecordingClient(String response) {
            this.provider = response;
            this.response = response;
        }

        private RecordingClient(String provider, String response) {
            this.provider = provider;
            this.response = response;
        }

        @Override
        public String providerType() {
            return provider;
        }

        @Override
        public LlmResponse generate(LlmRequest request) {
            calls++;
            return new LlmResponse(response, null, null);
        }
    }
}
