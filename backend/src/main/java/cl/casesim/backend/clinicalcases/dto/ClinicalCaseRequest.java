package cl.casesim.backend.clinicalcases.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ClinicalCaseRequest(
        @NotBlank(message = "El título es obligatorio.")
        String title,
        String description,
        String patientName,
        @JsonAlias({"age", "edad"})
        @Positive(message = "La edad del paciente debe ser mayor a 0.")
        Integer patientAge,
        String patientSex,
        @JsonAlias({"reason", "motivoConsulta", "motivo_consulta"})
        @NotBlank(message = "El motivo de consulta es obligatorio.")
        String chiefComplaint,
        String noInformationPhrase,
        Boolean active,
        List<@Valid ClinicalCaseFactRequest> facts,
        List<String> personality
) {

    public record ClinicalCaseFactRequest(
            @JsonAlias({"categoria"})
            String category,
            @JsonAlias({"title", "nombre"})
            String key,
            @JsonAlias({"contenido", "contenido_paciente"})
            @NotBlank(message = "El contenido del hecho clínico no puede estar vacío.")
            String content,
            @JsonAlias({"trigger", "disparador"})
            Object triggers,
            @JsonAlias({"nivel_revelacion"})
            @Min(value = 1, message = "El nivel de revelación debe ser mayor o igual a 1.")
            Integer revealLevel,
            String visibility
    ) {
    }
}
