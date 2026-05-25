package cl.casesim.backend.simulations.dto;

import java.util.UUID;

public record CreateSimulationResponse(
        UUID activityId,
        UUID clinicalCaseId,
        int assignedStudents
) {
}
