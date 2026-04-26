package cl.casesim.backend.sessions.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChatMessageRequest(
        @NotBlank String content
) {
}
