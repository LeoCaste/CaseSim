package cl.casesim.backend.professor.dto;

import java.util.UUID;

public record ProfessorStudentResponse(
        UUID id,
        String name,
        String email,
        boolean active
) {
}
