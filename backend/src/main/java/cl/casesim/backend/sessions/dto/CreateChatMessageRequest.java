package cl.casesim.backend.sessions.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChatMessageRequest(
        @NotBlank(message = "El contenido del mensaje no puede estar vacío.") String content
) {
}
