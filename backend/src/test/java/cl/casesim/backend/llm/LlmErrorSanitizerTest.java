package cl.casesim.backend.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmErrorSanitizerTest {

    private LlmProperties properties;
    private LlmErrorSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        properties = mock(LlmProperties.class);
        when(properties.getApiKey()).thenReturn("sk-my-secret-api-key");
        sanitizer = new LlmErrorSanitizer(properties);
    }

    @Test
    void sanitizaBearerToken() {
        String result = sanitizer.sanitizeError("Bearer sk-abc123");
        assertTrue(result.contains("Bearer ***"),
                "Debe reemplazar el token Bearer por 'Bearer ***'");
        assertFalse(result.contains("sk-abc123"),
                "No debe contener el token original");
    }

    @Test
    void sanitizaApiKeyEnHeader() {
        String result = sanitizer.sanitizeError("api-key=secret123");
        assertTrue(result.contains("api-key=***"),
                "Debe reemplazar el valor de api-key por ***");
        assertFalse(result.contains("secret123"),
                "No debe contener el valor original del api-key");
    }

    @Test
    void sanitizaXGoogApiKey() {
        String result = sanitizer.sanitizeError("x-goog-api-key=AIzaSyABC123DEF456");
        assertTrue(result.contains("x-goog-api-key=***"),
                "Debe reemplazar el valor de x-goog-api-key por ***");
        assertFalse(result.contains("AIzaSyABC123DEF456"),
                "No debe contener el valor original de x-goog-api-key");
    }

    @Test
    void sanitizaApiKeyConfiguradaEnProperties() {
        String result = sanitizer.sanitizeError("Error con api key sk-my-secret-api-key en el mensaje");
        assertTrue(result.contains("***"),
                "Debe reemplazar la apiKey configurada por ***");
        assertFalse(result.contains("sk-my-secret-api-key"),
                "No debe contener la apiKey configurada en texto plano");
    }

    @Test
    void manejaNullYBlank() {
        assertEquals("Error LLM no especificado.", sanitizer.sanitizeError(null),
                "Null debe retornar mensaje por defecto");
        assertEquals("Error LLM no especificado.", sanitizer.sanitizeError(""),
                "String vacío debe retornar mensaje por defecto");
        assertEquals("Error LLM no especificado.", sanitizer.sanitizeError("   "),
                "String con espacios debe retornar mensaje por defecto");
    }

    @Test
    void truncaAMaximo400Caracteres() {
        String longError = "A".repeat(500);
        String result = sanitizer.sanitizeError(longError);
        assertEquals(400, result.length(),
                "Debe truncar a 400 caracteres");
    }

    @Test
    void retornaStringOriginalSanitizadoSiEstaLimpio() {
        String cleanError = "Error de conexión con el proveedor LLM";
        String result = sanitizer.sanitizeError(cleanError);
        assertEquals(cleanError, result,
                "Debe retornar el string original si no contiene secretos");
    }

    @Test
    void sanitizaBearerTokenSinApiKeyEnProperties() {
        when(properties.getApiKey()).thenReturn("");
        LlmErrorSanitizer sanitizerNoApiKey = new LlmErrorSanitizer(properties);

        String result = sanitizerNoApiKey.sanitizeError("Bearer sk-abc123");
        assertTrue(result.contains("Bearer ***"),
                "Debe sanitarizar Bearer token aunque no haya apiKey configurada");
        assertFalse(result.contains("sk-abc123"),
                "No debe contener el token original");
    }

    @Test
    void sanitizacionMultiplesPatronesSimultaneos() {
        String error = "Bearer sk-abc123, api-key=secret456, x-goog-api-key=AIzaSyXYZ";
        String result = sanitizer.sanitizeError(error);

        assertTrue(result.contains("Bearer ***"));
        assertTrue(result.contains("api-key=***"));
        assertTrue(result.contains("x-goog-api-key=***"));
        assertFalse(result.contains("sk-abc123"));
        assertFalse(result.contains("secret456"));
        assertFalse(result.contains("AIzaSyXYZ"));
    }
}
