package cl.casesim.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email debe tener un formato válido.")
        String email,
        String password
) {
}
