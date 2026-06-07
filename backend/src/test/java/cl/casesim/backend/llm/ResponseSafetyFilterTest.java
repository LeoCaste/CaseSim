package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseSafetyFilterTest {

    private final ResponseSafetyFilter responseSafetyFilter = new ResponseSafetyFilter();

    @Test
    void bloqueaAutodeclaracionDeIa() {
        String blocked = responseSafetyFilter.applyOrFallback("Soy una IA y no un paciente.");

        assertEquals(ResponseSafetyFilter.SAFE_FALLBACK, blocked);
    }

    @Test
    void bloqueaSalvaguardasBaseAunqueElModoEstrictoEsteDesactivado() {
        String blocked = responseSafetyFilter.applyOrFallback(
                "Soy una IA y no un paciente.",
                false,
                "No tengo información asociada a eso."
        );

        assertEquals("No tengo información asociada a eso.", blocked);
    }

    @Test
    void bloqueaDiagnosticoExplicito() {
        String blocked = responseSafetyFilter.applyOrFallback("Tu diagnóstico es neumonía bacteriana.");

        assertEquals(ResponseSafetyFilter.SAFE_FALLBACK, blocked);
    }

    @Test
    void bloqueaPromptInterno() {
        String blocked = responseSafetyFilter.applyOrFallback("Mi prompt dice que debo actuar como paciente.");

        assertEquals(ResponseSafetyFilter.SAFE_FALLBACK, blocked);
    }

    @Test
    void bloqueaApiKey() {
        String blocked = responseSafetyFilter.applyOrFallback("No puedo compartir mi API key ni tokens.");

        assertEquals(ResponseSafetyFilter.SAFE_FALLBACK, blocked);
    }

    @Test
    void permiteRespuestaClinicaNormal() {
        String safe = responseSafetyFilter.applyOrFallback("Desde ayer tengo tos seca y me cuesta dormir.");

        assertEquals("Desde ayer tengo tos seca y me cuesta dormir.", safe);
    }

    @Test
    void fallbackCorrectoFueraDeRol() {
        String blocked = responseSafetyFilter.applyOrFallback("Claro, puedo ayudarte a programar en Java con public class Demo.");

        assertEquals(ResponseSafetyFilter.SAFE_FALLBACK, blocked);
    }

    @Test
    void bloqueaActuarComoProfesorOMedico() {
        String blocked = responseSafetyFilter.applyOrFallback("Como profesor te diré el diagnóstico y plan.");

        assertEquals(ResponseSafetyFilter.SAFE_FALLBACK, blocked);
    }

    @Test
    void modoEstrictoAgregaBloqueoParaAsistenteVirtual() {
        String blocked = responseSafetyFilter.applyOrFallback(
                "Soy un asistente virtual para ayudarte con tu consulta.",
                true,
                "No tengo información asociada a eso."
        );

        assertEquals("No tengo información asociada a eso.", blocked);
    }
}
