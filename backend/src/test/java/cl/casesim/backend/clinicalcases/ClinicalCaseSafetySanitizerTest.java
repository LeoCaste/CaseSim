package cl.casesim.backend.clinicalcases;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClinicalCaseSafetySanitizerTest {

    @Test
    void sanitizeCaseHistory_removesMetadataAndExpectedDiagnosis() {
        String sanitized = ClinicalCaseSafetySanitizer.sanitizeCaseHistory("""
                Relato seguro para el paciente.
                [CASESIM_META]
                expectedDiagnosis: Neumonía

                Evolución visible.
                diagnostico esperado: Influenza
                """);

        assertEquals("Relato seguro para el paciente.\nEvolución visible.", sanitized);
        assertFalse(sanitized.contains("[CASESIM_META]"));
        assertFalse(sanitized.contains("Neumonía"));
        assertFalse(sanitized.contains("Influenza"));
    }
}
