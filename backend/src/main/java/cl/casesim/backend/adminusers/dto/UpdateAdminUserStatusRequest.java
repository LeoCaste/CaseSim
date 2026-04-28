package cl.casesim.backend.adminusers.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAdminUserStatusRequest(
        @NotNull(message = "El estado activo es obligatorio.")
        Boolean active
) {
}
