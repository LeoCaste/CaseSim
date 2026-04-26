package cl.casesim.backend.clinicalcases.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClinicalCaseResponse(
        UUID id,
        String title,
        String description,
        String patientName,
        Integer patientAge,
        String patientSex,
        String chiefComplaint,
        String noInformationPhrase,
        boolean active,
        LocalDateTime createdAt
) {
}
