package cl.casesim.backend.sessions.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID activityId,
        UUID studentId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
