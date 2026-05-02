package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmProviderUrlResolverTest {

    @Test
    void openAiDefaultUrl() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("openai", null);
        assertEquals("https://api.openai.com/v1/chat/completions", url);
    }

    @Test
    void groqDefaultUrl() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("groq", null);
        assertEquals("https://api.groq.com/openai/v1/chat/completions", url);
    }

    @Test
    void normalizaPathChatCompletionsABaseUrl() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String base = resolver.resolveBaseUrl("openai", "https://api.openai.com/v1/chat/completions/");
        assertEquals("https://api.openai.com/v1", base);
    }

    @Test
    void baseUrlVacioUsaDefault() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String base = resolver.resolveBaseUrl("groq", "   ");
        assertEquals("https://api.groq.com/openai/v1", base);
    }

    @Test
    void openRouterDefaultUrl() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("openrouter", null);
        assertEquals("https://openrouter.ai/api/v1/chat/completions", url);
    }

    @Test
    void ollamaDefaultUrl() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("ollama", "");
        assertEquals("http://localhost:11434/v1/chat/completions", url);
    }

    @Test
    void openRouterAceptaLegacyChatCompletions() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String base = resolver.resolveBaseUrl("openrouter", "https://openrouter.ai/api/v1/chat/completions");
        assertEquals("https://openrouter.ai/api/v1", base);
    }

    @Test
    void providerDesconocidoFallaClaro() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> resolver.resolve("desconocido", null));
        assertEquals("Proveedor no soportado para resolver URL: desconocido", ex.getMessage());
    }

    @Test
    void groqNoUsaUrlOpenAi() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("groq", "https://api.openai.com/v1/chat/completions");
        assertEquals("https://api.groq.com/openai/v1/chat/completions", url);
    }

    @Test
    void openAiNoUsaUrlGroq() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("openai", "https://api.groq.com/openai/v1/chat/completions");
        assertEquals("https://api.openai.com/v1/chat/completions", url);
    }
}
