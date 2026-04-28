package cl.casesim.backend.sessions.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfessorSessionListItemResponse(
        UUID sessionId,
        StudentSummary student,
        ClinicalCaseSummary clinicalCase,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String finalDiagnosis,
        String finalReasoning,
        long messageCount,
        String basicSummary
) {
    public record StudentSummary(UUID id, String name, String email) {
    }

    public record ClinicalCaseSummary(UUID activityId, UUID clinicalCaseId, String title) {
    }
}
