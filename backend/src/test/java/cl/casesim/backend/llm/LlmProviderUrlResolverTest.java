package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmProviderUrlResolverTest {

    @Test
    void normalizaOpenAiSinDuplicarPath() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("openai", "https://api.openai.com/v1/chat/completions/");
        assertEquals("https://api.openai.com/v1/chat/completions", url);
    }

    @Test
    void normalizaGroqConPathOpenAiCompatible() {
        LlmProviderUrlResolver resolver = new LlmProviderUrlResolver();
        String url = resolver.resolve("groq", "https://api.groq.com/v1/chat/completions");
        assertEquals("https://api.groq.com/openai/v1/chat/completions", url);
    }
}
