package cl.casesim.backend.sessions.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID activityId,
        UUID clinicalCaseId,
        UUID studentId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        String finalDiagnosis,
        String finalReasoning,
        ClinicalCaseSummary clinicalCase
) {

    public record ClinicalCaseSummary(
            String patientName,
            Integer patientAge,
            String patientSex,
            String consultationReason
    ) {
    }
}
