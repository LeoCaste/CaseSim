package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmClientRouterTest {

    @Test
    void seleccionaGroqCuandoProviderEsGroq() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("groq");

        RecordingClient defaultClient = new RecordingClient("default");
        RecordingClient groqClient = new RecordingClient("groq");

        LlmClientRouter router = new LlmClientRouter(properties, defaultClient, groqClient);

        String result = router.generateChatCompletion(List.of(new LlmClient.ChatPromptMessage("user", "hola")), 0.4, 100);

        assertEquals("groq", result);
        assertEquals(0, defaultClient.calls);
        assertEquals(1, groqClient.calls);
    }

    @Test
    void seleccionaDefaultCuandoProviderNoEsGroq() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("openai");

        RecordingClient defaultClient = new RecordingClient("default");
        RecordingClient groqClient = new RecordingClient("groq");

        LlmClientRouter router = new LlmClientRouter(properties, defaultClient, groqClient);

        String result = router.generateChatCompletion(List.of(new LlmClient.ChatPromptMessage("user", "hola")), 0.4, 100);

        assertEquals("default", result);
        assertEquals(1, defaultClient.calls);
        assertEquals(0, groqClient.calls);
    }

    private static class RecordingClient implements LlmClient {
        private final String response;
        private int calls = 0;

        private RecordingClient(String response) {
            this.response = response;
        }

        @Override
        public String generateChatCompletion(List<ChatPromptMessage> messages) {
            calls++;
            return response;
        }

        @Override
        public String generateChatCompletion(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
            calls++;
            return response;
        }
    }
}
