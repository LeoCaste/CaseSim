package cl.casesim.backend.simulations;

import java.util.UUID;

public interface StudentActivityProjection {

    UUID getActivityId();

    UUID getClinicalCaseId();

    String getTitle();

    String getStatus();
}
