package cl.casesim.backend.adminusers.dto;

import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String name,
        String email,
        boolean active,
        Set<String> roles
) {
}
