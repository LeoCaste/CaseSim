package cl.casesim.backend.sessions.dto;

import jakarta.validation.constraints.NotBlank;

public record FinalDiagnosisRequest(
        @NotBlank(message = "El diagnóstico final es obligatorio.") String diagnosis,
        @NotBlank(message = "El razonamiento final es obligatorio.") String reasoning
) {
}
