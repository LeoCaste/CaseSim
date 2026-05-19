package cl.casesim.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetTokenRequest(
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email debe tener un formato válido.")
        @Size(max = 150, message = "El email supera el largo máximo permitido (150 caracteres).")
        String email
) {
}
