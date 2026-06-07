package cl.casesim.backend.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientResponseSafetyServiceTest {

    private ResponseSafetyFilter responseSafetyFilter;
    private PatientResponseSafetyService service;

    @BeforeEach
    void setUp() {
        responseSafetyFilter = mock(ResponseSafetyFilter.class);
        service = new PatientResponseSafetyService(responseSafetyFilter);
    }

    @Test
    void respuestaSeguraPasaSinCambios() {
        when(responseSafetyFilter.applyOrFallback("Me duele el abdomen", true, "No sé eso"))
                .thenReturn("Me duele el abdomen");

        String result = service.applyLlmResponse("Me duele el abdomen", true, "No sé eso");

        assertEquals("Me duele el abdomen", result);
        verify(responseSafetyFilter).applyOrFallback("Me duele el abdomen", true, "No sé eso");
    }

    @Test
    void respuestaInseguraUsaNoInformationPhrase() {
        when(responseSafetyFilter.applyOrFallback("Como IA...", true, "No tengo información asociada a eso."))
                .thenReturn("No tengo información asociada a eso.");

        String result = service.applyLlmResponse("Como IA...", true, "No tengo información asociada a eso.");

        assertEquals("No tengo información asociada a eso.", result);
        verify(responseSafetyFilter).applyOrFallback("Como IA...", true, "No tengo información asociada a eso.");
    }

    @Test
    void fallbackContextualSeFiltraConElMismoTextoComoFallback() {
        String fallback = "No pude cargar el contexto clínico de esta sesión. Intenta nuevamente en unos segundos o reinicia la sesión.";
        when(responseSafetyFilter.applyOrFallback(fallback, false, fallback)).thenReturn(fallback);

        String result = service.applyContextualFallback(fallback, false);

        assertEquals(fallback, result);
        verify(responseSafetyFilter).applyOrFallback(fallback, false, fallback);
    }

    @Test
    void fallbackTecnicoSeFiltraConElMismoTextoComoFallback() {
        String fallback = "Perdón, me cuesta responder en este momento. ¿Podrías repetir tu pregunta?";
        when(responseSafetyFilter.applyOrFallback(fallback, true, fallback)).thenReturn(fallback);

        String result = service.applyTechnicalFallback(fallback, true);

        assertEquals(fallback, result);
        verify(responseSafetyFilter).applyOrFallback(fallback, true, fallback);
    }

    @Test
    void fallbackLocalPacienteSeFiltraConNoInformationPhrase() {
        when(responseSafetyFilter.applyOrFallback("Hola, soy Paciente. Dolor abdominal", true, "No tengo información."))
                .thenReturn("Hola, soy Paciente. Dolor abdominal");

        String result = service.applyLocalPatientFallback("Hola, soy Paciente. Dolor abdominal", true, "No tengo información.");

        assertEquals("Hola, soy Paciente. Dolor abdominal", result);
        verify(responseSafetyFilter).applyOrFallback("Hola, soy Paciente. Dolor abdominal", true, "No tengo información.");
    }

    @Test
    void noInformationPhraseSeRespetaEnAlternativasPorRepeticion() {
        when(responseSafetyFilter.applyOrFallback("fact inseguro", true, "No tengo información asociada a eso."))
                .thenReturn("No tengo información asociada a eso.");

        String result = service.applyRepetitionAlternative("fact inseguro", true, "No tengo información asociada a eso.");

        assertEquals("No tengo información asociada a eso.", result);
        verify(responseSafetyFilter).applyOrFallback("fact inseguro", true, "No tengo información asociada a eso.");
    }

    @Test
    void enabledSafetyFilterSePropagaAlFiltro() {
        when(responseSafetyFilter.applyOrFallback("respuesta", false, "fallback"))
                .thenReturn("respuesta");

        String result = service.applyLlmResponse("respuesta", false, "fallback");

        assertEquals("respuesta", result);
        verify(responseSafetyFilter).applyOrFallback("respuesta", false, "fallback");
    }
}
