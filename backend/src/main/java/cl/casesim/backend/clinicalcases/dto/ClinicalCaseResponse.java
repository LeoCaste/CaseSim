package cl.casesim.backend.clinicalcases.dto;

import cl.casesim.backend.clinicalcases.ClinicalCaseStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicalCaseResponse(
        UUID id,
        String title,
        String description,
        String patientName,
        Integer patientAge,
        String patientSex,
        String chiefComplaint,
        String noInformationPhrase,
        boolean active,
        ClinicalCaseStatus status,
        Integer estimatedTimeMinutes,
        LocalDateTime createdAt,
        List<ClinicalCaseFactResponse> facts,
        List<String> personality
) {

    public ClinicalCaseResponse(
            UUID id,
            String title,
            String description,
            String patientName,
            Integer patientAge,
            String patientSex,
            String chiefComplaint,
            String noInformationPhrase,
            boolean active,
            LocalDateTime createdAt,
            List<ClinicalCaseFactResponse> facts,
            List<String> personality
    ) {
        this(id, title, description, patientName, patientAge, patientSex, chiefComplaint, noInformationPhrase,
                active, ClinicalCaseStatus.fromLegacyActive(active), null, createdAt, facts, personality);
    }

    public record ClinicalCaseFactResponse(
            String category,
            String key,
            String content,
            List<String> triggers,
            Integer revealLevel
    ) {
        @JsonProperty("contenido")
        public String contenido() {
            return content;
        }

        @JsonProperty("contenido_paciente")
        public String contenidoPaciente() {
            return content;
        }
    }
}
