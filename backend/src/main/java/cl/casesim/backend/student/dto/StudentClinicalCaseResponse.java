package cl.casesim.backend.student.dto;

import java.util.UUID;

public record StudentClinicalCaseResponse(
        UUID activityId,
        UUID clinicalCaseId,
        String title,
        String patientName,
        Integer patientAge,
        String patientSex,
        String chiefComplaint
) {
}
