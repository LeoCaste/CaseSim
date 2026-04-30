package cl.casesim.backend.clinicalcases.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ClinicalCaseRequest(
        @NotBlank(message = "El título es obligatorio.")
        String title,
        String description,
        String patientName,
        @Positive(message = "La edad del paciente debe ser mayor a 0.")
        Integer patientAge,
        String patientSex,
        @NotBlank(message = "El motivo de consulta es obligatorio.")
        String chiefComplaint,
        String noInformationPhrase,
        Boolean active,
        List<@Valid ClinicalCaseFactRequest> facts,
        List<String> personality
) {

    public record ClinicalCaseFactRequest(
            String key,
            @NotBlank(message = "El contenido del hecho clínico no puede estar vacío.")
            String content,
            List<String> triggers,
            @NotNull(message = "El nivel de revelación es obligatorio.")
            @Min(value = 1, message = "El nivel de revelación debe ser mayor o igual a 1.")
            Integer revealLevel
    ) {
    }
}
