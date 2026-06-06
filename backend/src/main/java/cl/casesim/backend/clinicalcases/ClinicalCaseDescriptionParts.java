package cl.casesim.backend.clinicalcases;

import java.util.Map;

public record ClinicalCaseDescriptionParts(
        String clinicalContext,
        Map<String, String> legacyMetadata,
        Map<String, String> teachingFields
) {
}
