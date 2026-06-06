package cl.casesim.backend.clinicalcases;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClinicalCaseDescriptionParserTest {

    @Test
    void parseSeparatesLegacyMetadataTeachingFieldsAndSafeClinicalContext() {
        ClinicalCaseDescriptionParts parts = ClinicalCaseDescriptionParser.parse("""
                Relato seguro para paciente.
                [CASESIM_META]
                expectedDiagnosis: Neumonía
                objetivoDocente: practicar anamnesis
                legacyFlag: demo

                Evolución visible.
                finalDiagnosis: Influenza
                """);

        assertEquals("Relato seguro para paciente.\nEvolución visible.", parts.clinicalContext());
        assertEquals("Neumonía", parts.legacyMetadata().get("expectedDiagnosis"));
        assertEquals("demo", parts.legacyMetadata().get("legacyFlag"));
        assertEquals("practicar anamnesis", parts.teachingFields().get("objetivoDocente"));
        assertFalse(parts.clinicalContext().contains("[CASESIM_META]"));
        assertFalse(parts.clinicalContext().contains("Neumonía"));
        assertFalse(parts.clinicalContext().contains("Influenza"));
    }

    @Test
    void parseSupportsDescriptionsWithoutLegacyMetadata() {
        ClinicalCaseDescriptionParts parts = ClinicalCaseDescriptionParser.parse("Historia clínica visible.\nControl previo estable.");

        assertEquals("Historia clínica visible.\nControl previo estable.", parts.clinicalContext());
        assertEquals(0, parts.legacyMetadata().size());
        assertEquals(0, parts.teachingFields().size());
    }

    @Test
    void parseReturnsEmptyPartsForBlankDescription() {
        ClinicalCaseDescriptionParts parts = ClinicalCaseDescriptionParser.parse("  ");

        assertNull(parts.clinicalContext());
        assertEquals(0, parts.legacyMetadata().size());
        assertEquals(0, parts.teachingFields().size());
    }
}
