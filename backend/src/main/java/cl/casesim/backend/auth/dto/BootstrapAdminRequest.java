package cl.casesim.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BootstrapAdminRequest(
        @NotBlank(message = "El nombre de la institución es obligatorio.")
        @Size(max = 200, message = "El nombre de la institución supera el largo máximo permitido (200 caracteres).")
        String institutionName,

        @NotBlank(message = "El nombre del administrador es obligatorio.")
        @Size(max = 120, message = "El nombre del administrador supera el largo máximo permitido (120 caracteres).")
        String adminName,

        @NotBlank(message = "El email del administrador es obligatorio.")
        @Email(message = "El email del administrador debe tener un formato válido.")
        @Size(max = 150, message = "El email del administrador supera el largo máximo permitido (150 caracteres).")
        String adminEmail,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
        @Pattern(regexp = ".*[A-Z].*", message = "La contraseña debe incluir al menos 1 mayúscula.")
        @Pattern(regexp = ".*\\d.*", message = "La contraseña debe incluir al menos 1 número.")
        String password,

        @NotBlank(message = "La confirmación de contraseña es obligatoria.")
        String confirmPassword
) {
}
