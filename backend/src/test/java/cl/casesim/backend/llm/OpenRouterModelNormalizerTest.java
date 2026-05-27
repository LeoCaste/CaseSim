package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenRouterModelNormalizerTest {

    @Test
    void normalizeClaudeSinProviderAgregaPrefijoAnthropic() {
        assertEquals(
                "anthropic/claude-3.7-sonnet",
                OpenRouterModelNormalizer.normalize("claude-3.7-sonnet")
        );
    }

    @Test
    void normalizeModeloNoClaudeNoLoModifica() {
        assertEquals(
                "openai/gpt-4.1-mini",
                OpenRouterModelNormalizer.normalize("openai/gpt-4.1-mini")
        );
    }

    @Test
    void normalizeAliasConocidoDeHaikuLoExpande() {
        assertEquals(
                "anthropic/claude-3.5-haiku-20241022",
                OpenRouterModelNormalizer.normalize("claude-3.5-haiku")
        );
    }
}
