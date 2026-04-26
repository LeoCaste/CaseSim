package cl.casesim.backend.sessions.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID activityId,
        @NotNull UUID studentId
) {
}
