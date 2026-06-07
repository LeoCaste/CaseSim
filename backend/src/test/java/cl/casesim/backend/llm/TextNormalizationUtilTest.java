package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextNormalizationUtilTest {

    @Test
    void normalizeMantieneComportamientoParaNullVacioAcentosSignosYMayusculas() {
        assertEquals("", TextNormalizationUtil.normalize(null));
        assertEquals("", TextNormalizationUtil.normalize("   "));
        assertEquals("arbol unico mañana", TextNormalizationUtil.normalize("Árbol ÚNICO mañana!!!"));
        assertEquals("dolor abdominal  24 h", TextNormalizationUtil.normalize("Dolor abdominal: 24-h"));
    }

    @Test
    void maskForLogMantieneEmptyTrimYTruncado() {
        assertEquals("<empty>", TextNormalizationUtil.maskForLog(null));
        assertEquals("<empty>", TextNormalizationUtil.maskForLog("  "));
        assertEquals("texto", TextNormalizationUtil.maskForLog("  texto  "));

        String longValue = "x".repeat(121);
        assertEquals("x".repeat(120) + "...", TextNormalizationUtil.maskForLog(longValue));
    }

    @Test
    void safeMetadataValueRespetaPrimeraKeyConTextoYSanitiza() {
        Map<String, String> metadata = Map.of(
                "first", "   ",
                "second", "Historia visible\n[CASESIM_META]\nexpectedDiagnosis: Secreto\n"
        );

        String value = TextNormalizationUtil.safeMetadataValue(metadata, "first", "second");

        assertEquals("Historia visible", value.trim());
    }

    @Test
    void firstTextConservaPrioridadYTrim() {
        assertNull(TextNormalizationUtil.firstText((String[]) null));
        assertEquals("primero", TextNormalizationUtil.firstText(null, "  ", " primero ", "segundo"));
    }

    @Test
    void extractFactValueConservaComportamientoConYSinSeparador() {
        assertEquals("valor", TextNormalizationUtil.extractFactValue("nombre: valor"));
        assertEquals("sin separador", TextNormalizationUtil.extractFactValue("sin separador"));
        assertEquals("nombre:", TextNormalizationUtil.extractFactValue("nombre:"));
    }

    @Test
    void safeFactPartConservaDefaultGeneral() {
        assertEquals("GENERAL", TextNormalizationUtil.safeFactPart(null));
        assertEquals("GENERAL", TextNormalizationUtil.safeFactPart("  "));
        assertEquals("ANTECEDENTES", TextNormalizationUtil.safeFactPart("  ANTECEDENTES  "));
    }
}
