package cl.casesim.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "El token es obligatorio.")
        String token,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
        @Pattern(regexp = ".*[A-Z].*", message = "La contraseña debe incluir al menos 1 mayúscula.")
        @Pattern(regexp = ".*\\d.*", message = "La contraseña debe incluir al menos 1 número.")
        String password,

        @NotBlank(message = "La confirmación de contraseña es obligatoria.")
        String confirmPassword
) {
}
