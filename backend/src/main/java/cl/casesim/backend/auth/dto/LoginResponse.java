package cl.casesim.backend.auth.dto;

public record LoginResponse(
        String token,
        AuthUserResponse user
) {
}
