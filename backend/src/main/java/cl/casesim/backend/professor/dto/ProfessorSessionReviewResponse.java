package cl.casesim.backend.professor.dto;

import cl.casesim.backend.sessions.dto.ChatMessageResponse;

import java.util.List;
import java.util.UUID;

public record ProfessorSessionReviewResponse(
        Session session,
        List<ChatMessageResponse> transcript,
        Notebook notebook,
        Diagnosis diagnosis
) {
    public record Session(
            UUID id,
            String studentName,
            String activityName,
            String caseName,
            String status,
            int durationMinutes,
            int turns,
            String submittedAt
    ) {
    }

    public record Notebook(String notes, String hypothesis) {
    }

    public record Diagnosis(String finalDiagnosis, String reasoning) {
    }
}
