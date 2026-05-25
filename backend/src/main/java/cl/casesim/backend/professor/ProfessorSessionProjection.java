package cl.casesim.backend.professor;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ProfessorSessionProjection {

    UUID getSessionId();

    String getStudentName();

    String getActivityName();

    String getCaseName();

    String getStatus();

    LocalDateTime getStartedAt();

    LocalDateTime getFinishedAt();

    String getFinalDiagnosis();

    String getFinalReasoning();

    Integer getTurns();
}
