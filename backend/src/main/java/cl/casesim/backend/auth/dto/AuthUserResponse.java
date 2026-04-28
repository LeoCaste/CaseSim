package cl.casesim.backend.auth.dto;

import java.util.Set;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String name,
        String email,
        Set<String> roles,
        boolean active
) {
}
