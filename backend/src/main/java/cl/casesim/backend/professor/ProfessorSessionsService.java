package cl.casesim.backend.professor;

import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.professor.dto.ProfessorSessionListItemResponse;
import cl.casesim.backend.professor.dto.ProfessorSessionReviewResponse;
import cl.casesim.backend.sessions.ChatMessageRepository;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ProfessorSessionsService {

    private final SimulationSessionRepository simulationSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ProfessorSessionsService(
            SimulationSessionRepository simulationSessionRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.simulationSessionRepository = simulationSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfessorSessionListItemResponse> getSessions(UUID professorId, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 100));
        return simulationSessionRepository.findProfessorSessionSummaries(professorId, PageRequest.of(0, resolvedLimit))
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfessorSessionReviewResponse getSessionReview(UUID professorId, UUID sessionId) {
        ProfessorSessionProjection summary = simulationSessionRepository
                .findProfessorSessionSummaryById(sessionId, professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada para revisión: " + sessionId));

        List<ChatMessageResponse> transcript = chatMessageRepository.findBySesionIdOrderByNumeroTurnoAsc(sessionId)
                .stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getSesionId(),
                        message.getRol(),
                        message.getContenido(),
                        message.getNumeroTurno(),
                        message.getCreadoEn()
                ))
                .toList();

        return new ProfessorSessionReviewResponse(
                new ProfessorSessionReviewResponse.Session(
                        summary.getSessionId(),
                        summary.getStudentName(),
                        summary.getActivityName(),
                        summary.getCaseName(),
                        toFrontendStatus(summary.getStatus()),
                        calculateDurationMinutes(summary),
                        summary.getTurns() == null ? 0 : summary.getTurns(),
                        summary.getFinishedAt() == null ? null : summary.getFinishedAt().toString()
                ),
                transcript,
                new ProfessorSessionReviewResponse.Notebook("", ""),
                new ProfessorSessionReviewResponse.Diagnosis(
                        summary.getFinalDiagnosis() == null ? "" : summary.getFinalDiagnosis(),
                        summary.getFinalReasoning() == null ? "" : summary.getFinalReasoning()
                )
        );
    }

    private ProfessorSessionListItemResponse toListItem(ProfessorSessionProjection projection) {
        return new ProfessorSessionListItemResponse(
                projection.getSessionId(),
                projection.getStudentName(),
                projection.getActivityName(),
                projection.getCaseName(),
                toFrontendStatus(projection.getStatus()),
                projection.getTurns() == null ? 0 : projection.getTurns(),
                calculateDurationMinutes(projection),
                projection.getFinishedAt()
        );
    }

    private String toFrontendStatus(String status) {
        if ("FINALIZADA".equals(status)) {
            return "COMPLETED";
        }
        return "IN_PROGRESS";
    }

    private Integer calculateDurationMinutes(ProfessorSessionProjection projection) {
        if (projection.getStartedAt() == null || projection.getFinishedAt() == null) {
            return null;
        }
        return Math.toIntExact(Math.max(0, Duration.between(projection.getStartedAt(), projection.getFinishedAt()).toMinutes()));
    }
}
