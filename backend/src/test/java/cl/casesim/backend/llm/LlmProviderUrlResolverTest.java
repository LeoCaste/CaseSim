package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void geminiBaseDefaultYGenerateContentPath() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String base = resolver.resolveBaseUrl("gemini", null);
        String url = resolver.resolveGeminiGenerateContentUrl(null, "gemini-2.5-flash-lite");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models", base);
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent", url);
    }

    @Test
    void resolveGeminiConMetodoGenericoFallaClaro() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> resolver.resolve("gemini", null));
        assertEquals("Use resolveGeminiGenerateContentUrl para provider gemini.", ex.getMessage());
    }

    @Test
    void geminiConfigBaseSinModelsAgregaSegmentoCorrecto() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String configuredBase = "https://generativelanguage.googleapis.com/v1beta";
        String url = resolver.resolveGeminiGenerateContentUrl(configuredBase, "gemini-2.5-flash-lite");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent", url);
        assertNotEquals("https://generativelanguage.googleapis.com/v1beta/gemini-2.5-flash-lite:generateContent", url);
    }

    @Test
    void geminiConfigConEndpointCompletoNormalizaYEvitaDuplicarModelo() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String configuredEndpoint = "https://generativelanguage.googleapis.com/v1beta/gemini-2.5-flash-lite:generateContent";
        String url = resolver.resolveGeminiGenerateContentUrl(configuredEndpoint, "gemini-2.5-flash-lite");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent", url);
    }
}
