package cl.casesim.backend.llm.provider.gemini;

import cl.casesim.backend.llm.LlmMessage;
import cl.casesim.backend.llm.LlmProperties;
import cl.casesim.backend.llm.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiMappersTest {

    @Test
    void requestMapperConstruyeGenerateContentConSystemInstruction() {
        GeminiRequestMapper mapper = new GeminiRequestMapper();
        LlmProperties properties = new LlmProperties();
        properties.setTemperature(0.4);
        properties.setMaxTokens(350);

        LlmRequest request = new LlmRequest(
                List.of(
                        new LlmMessage("system", "regla 1"),
                        new LlmMessage("user", "hola"),
                        new LlmMessage("assistant", "respuesta")
                ),
                null,
                0.7,
                222
        );

        Map<String, Object> payload = mapper.toGenerateContentPayload(request, properties);

        assertNotNull(payload.get("systemInstruction"));
        assertNotNull(payload.get("contents"));
        assertNotNull(payload.get("generationConfig"));
    }

    @Test
    void responseMapperConcatenaTextoYMapeaUsage() {
        GeminiResponseMapper mapper = new GeminiResponseMapper();
        Map<String, Object> response = Map.of(
                "candidates", List.of(
                        Map.of("content", Map.of("parts", List.of(Map.of("text", "uno"), Map.of("text", "dos"))))
                ),
                "usageMetadata", Map.of(
                        "promptTokenCount", 10,
                        "candidatesTokenCount", 20,
                        "totalTokenCount", 30
                )
        );

        assertEquals("uno\ndos", mapper.extractContent(response));
        assertEquals(10, mapper.promptTokens(response));
        assertEquals(20, mapper.completionTokens(response));
        assertEquals(30, mapper.totalTokens(response));
    }

    @Test
    void responseMapperFallaCuandoNoHayContenido() {
        GeminiResponseMapper mapper = new GeminiResponseMapper();
        assertThrows(RuntimeException.class, () -> mapper.extractContent(Map.of()));
    }
}
