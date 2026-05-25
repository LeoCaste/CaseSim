package cl.casesim.backend.sessions.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID sessionId,
        String role,
        String content,
        Integer turnNumber,
        LocalDateTime createdAt
) {
}
