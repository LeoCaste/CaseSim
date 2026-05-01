package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiLlmClientTest {

    @Test
    void extractOpenAiResponsesContentUsaOutputTextDirecto() {
        LlmProperties properties = new LlmProperties();
        OpenAiLlmClient client = new OpenAiLlmClient(properties);

        String content = client.extractOpenAiResponsesContent(Map.of("output_text", " Hola desde Responses API "));

        assertEquals("Hola desde Responses API", content);
    }

    @Test
    void extractOpenAiResponsesContentConcatenaBloquesDeOutput() {
        LlmProperties properties = new LlmProperties();
        OpenAiLlmClient client = new OpenAiLlmClient(properties);

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
}
