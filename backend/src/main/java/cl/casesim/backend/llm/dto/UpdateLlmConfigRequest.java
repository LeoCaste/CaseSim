package cl.casesim.backend.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateLlmConfigRequest(
        @NotBlank(message = "El proveedor es obligatorio.")
        @Size(max = 80, message = "El proveedor no puede superar 80 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "El proveedor contiene caracteres inválidos.")
        String provider,

        @NotBlank(message = "El modelo es obligatorio.")
        @Size(max = 100, message = "El modelo no puede superar 100 caracteres.")
        String model,

        @NotBlank(message = "La URL base es obligatoria.")
        @Size(max = 500, message = "La URL base no puede superar 500 caracteres.")
        String baseUrl,

        @NotNull(message = "El estado habilitado es obligatorio.")
        Boolean enabled,

        @Size(max = 500, message = "La API key no puede superar 500 caracteres.")
        String apiKey
) {
}
