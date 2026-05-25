package cl.casesim.backend.sessions;

import cl.casesim.backend.auth.AppUser;
import cl.casesim.backend.auth.UserRepository;
import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import cl.casesim.backend.sessions.dto.ProfessorSessionDetailResponse;
import cl.casesim.backend.sessions.dto.ProfessorSessionListItemResponse;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProfessorSessionService {

    private final SimulationSessionRepository simulationSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimulationActivityRepository simulationActivityRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;

    public ProfessorSessionService(
            SimulationSessionRepository simulationSessionRepository,
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            SimulationActivityRepository simulationActivityRepository,
            ClinicalCaseRepository clinicalCaseRepository
    ) {
        this.simulationSessionRepository = simulationSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.simulationActivityRepository = simulationActivityRepository;
        this.clinicalCaseRepository = clinicalCaseRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfessorSessionListItemResponse> getProfessorSessions(UUID professorId) {
        return simulationSessionRepository.findProfessorVisibleSessions(professorId, PageRequest.of(0, 200))
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfessorSessionDetailResponse getProfessorSessionById(UUID professorId, UUID sessionId) {
        SimulationSession session = simulationSessionRepository
                .findProfessorVisibleSessionById(sessionId, professorId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con id: " + sessionId));

        AppUser student = requireStudent(session.getEstudianteId());
        SimulationActivity activity = requireActivity(session.getActividadId());
        ClinicalCase clinicalCase = requireClinicalCase(activity.getCasoId());

        List<ChatMessageResponse> conversation = chatMessageRepository.findBySesionIdOrderByNumeroTurnoAsc(session.getId())
                .stream()
                .map(this::toChatMessageResponse)
                .toList();

        return new ProfessorSessionDetailResponse(
                session.getId(),
                session.getEstado(),
                session.getIniciadaEn(),
                session.getFinalizadaEn(),
                session.getCreadaEn(),
                new ProfessorSessionDetailResponse.StudentDetail(student.getId(), student.getNombre(), student.getEmail()),
                new ProfessorSessionDetailResponse.ClinicalCaseDetail(
                        activity.getId(),
                        clinicalCase.getId(),
                        clinicalCase.getTitulo(),
                        clinicalCase.getDescripcion(),
                        clinicalCase.getPacienteNombre(),
                        clinicalCase.getPacienteEdad(),
                        clinicalCase.getPacienteSexo(),
                        clinicalCase.getMotivoConsulta()
                ),
                conversation,
                session.getDiagnosticoFinal(),
                session.getRazonamientoFinal()
        );
    }

    private ProfessorSessionListItemResponse toListItem(SimulationSession session) {
        AppUser student = requireStudent(session.getEstudianteId());
        SimulationActivity activity = requireActivity(session.getActividadId());
        ClinicalCase clinicalCase = requireClinicalCase(activity.getCasoId());
        long messageCount = chatMessageRepository.countBySesionId(session.getId());

        String basicSummary = buildBasicSummary(session, clinicalCase);

        return new ProfessorSessionListItemResponse(
                session.getId(),
                new ProfessorSessionListItemResponse.StudentSummary(student.getId(), student.getNombre(), student.getEmail()),
                new ProfessorSessionListItemResponse.ClinicalCaseSummary(activity.getId(), clinicalCase.getId(), clinicalCase.getTitulo()),
                session.getEstado(),
                session.getIniciadaEn(),
                session.getFinalizadaEn(),
                session.getDiagnosticoFinal(),
                session.getRazonamientoFinal(),
                messageCount,
                basicSummary
        );
    }

    private String buildBasicSummary(SimulationSession session, ClinicalCase clinicalCase) {
        if (session.getDiagnosticoFinal() != null && !session.getDiagnosticoFinal().isBlank()) {
            return abbreviate("Dx final: " + session.getDiagnosticoFinal().trim(), 180);
        }
        if (clinicalCase.getMotivoConsulta() != null && !clinicalCase.getMotivoConsulta().isBlank()) {
            return abbreviate("Motivo: " + clinicalCase.getMotivoConsulta().trim(), 180);
        }
        return "Sesión " + session.getEstado();
    }

    private String abbreviate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private AppUser requireStudent(UUID studentId) {
        return userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado con id: " + studentId));
    }

    private SimulationActivity requireActivity(UUID activityId) {
        return simulationActivityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada con id: " + activityId));
    }

    private ClinicalCase requireClinicalCase(UUID caseId) {
        return clinicalCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Caso clínico no encontrado con id: " + caseId));
    }

    private ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSesionId(),
                message.getRol(),
                message.getContenido(),
                message.getNumeroTurno(),
                message.getCreadoEn()
        );
    }
}
