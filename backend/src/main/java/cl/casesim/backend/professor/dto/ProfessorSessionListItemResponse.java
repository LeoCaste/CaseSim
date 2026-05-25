package cl.casesim.backend.professor.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfessorSessionListItemResponse(
        UUID id,
        String studentName,
        String activityName,
        String caseName,
        String status,
        int turns,
        Integer durationMinutes,
        LocalDateTime submittedAt
) {
}
