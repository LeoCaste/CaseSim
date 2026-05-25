package cl.casesim.backend.simulations.dto;

import java.util.UUID;

public record StudentActivityResponse(
        UUID activityId,
        UUID clinicalCaseId,
        String title,
        String status
) {
}
