package cl.casesim.backend.sessions.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProfessorSessionDetailResponse(
        UUID sessionId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        StudentDetail student,
        ClinicalCaseDetail clinicalCase,
        List<ChatMessageResponse> conversation,
        String finalDiagnosis,
        String finalReasoning
) {
    public record StudentDetail(UUID id, String name, String email) {
    }

    public record ClinicalCaseDetail(
            UUID activityId,
            UUID clinicalCaseId,
            String title,
            String description,
            String patientName,
            Integer patientAge,
            String patientSex,
            String consultationReason
    ) {
    }
}
