package cl.casesim.backend.clinicalcases;

public final class ClinicalCaseSafetySanitizer {

    public static final String GENERIC_CASE_NAME = "Caso clínico asignado";

    private ClinicalCaseSafetySanitizer() {
    }

    public static String safeCaseTitle() {
        return GENERIC_CASE_NAME;
    }

    public static String sanitizeCaseHistory(String value) {
        return ClinicalCaseDescriptionParser.parse(value).clinicalContext();
    }
}
