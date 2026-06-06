package cl.casesim.backend.clinicalcases.dto;

import cl.casesim.backend.clinicalcases.ClinicalCaseStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ClinicalCaseRequest(
        String title,
        String description,
        String patientName,
        @JsonAlias({"age", "edad"})
        @Positive(message = "La edad del paciente debe ser mayor a 0.")
        Integer patientAge,
        String patientSex,
        @JsonAlias({"reason", "motivoConsulta", "motivo_consulta"})
        String chiefComplaint,
        String noInformationPhrase,
        Boolean active,
        ClinicalCaseStatus status,
        @Min(value = 5, message = "La duración estimada debe ser al menos 5 minutos.")
        @Max(value = 180, message = "La duración estimada no puede exceder 180 minutos.")
        Integer estimatedTimeMinutes,
        List<@Valid ClinicalCaseFactRequest> facts,
        List<String> personality
) {

    public ClinicalCaseRequest(
            String title,
            String description,
            String patientName,
            Integer patientAge,
            String patientSex,
            String chiefComplaint,
            String noInformationPhrase,
            Boolean active,
            List<@Valid ClinicalCaseFactRequest> facts,
            List<String> personality
    ) {
        this(title, description, patientName, patientAge, patientSex, chiefComplaint, noInformationPhrase, active, null, null, facts, personality);
    }

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
