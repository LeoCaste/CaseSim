package cl.casesim.backend.auth.dto;

public record PreCheckResponse(
        boolean requiresPassword
) {
}
