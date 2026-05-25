package cl.casesim.backend.adminusers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminUserRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
        String name,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "El email es inválido.")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres.")
        String email,

        @NotBlank(message = "El rol es obligatorio.")
        String role,

        @Size(max = 200, message = "La contraseña no puede superar los 200 caracteres.")
        String password
) {
}
