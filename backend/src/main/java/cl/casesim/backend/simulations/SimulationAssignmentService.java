package cl.casesim.backend.simulations;

import cl.casesim.backend.auth.AppUser;
import cl.casesim.backend.auth.UserRepository;
import cl.casesim.backend.auth.UserRole;
import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseStatus;
import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ConflictException;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.dto.CreateSimulationRequest;
import cl.casesim.backend.simulations.dto.CreateSimulationResponse;
import cl.casesim.backend.simulations.dto.StudentActivityResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SimulationAssignmentService {

    private static final String SESSION_PENDING = "PENDIENTE";
    private static final String ACTIVITY_FORMATIVE = "FORMATIVO";

    private final ClinicalCaseRepository clinicalCaseRepository;
    private final UserRepository userRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final SimulationActivityRepository simulationActivityRepository;
    private final SimulationSessionRepository simulationSessionRepository;

    public SimulationAssignmentService(
            ClinicalCaseRepository clinicalCaseRepository,
            UserRepository userRepository,
            CourseEnrollmentRepository courseEnrollmentRepository,
            SimulationActivityRepository simulationActivityRepository,
            SimulationSessionRepository simulationSessionRepository
    ) {
        this.clinicalCaseRepository = clinicalCaseRepository;
        this.userRepository = userRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.simulationActivityRepository = simulationActivityRepository;
        this.simulationSessionRepository = simulationSessionRepository;
    }

    @Transactional
    public CreateSimulationResponse createSimulation(CreateSimulationRequest request, UUID assignedBy) {
        ClinicalCase clinicalCase = clinicalCaseRepository.findById(request.clinicalCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Caso clínico no encontrado."));
        if (clinicalCase.getStatus() != ClinicalCaseStatus.READY) {
            throw new BadRequestException("Este caso aún no está listo para ser asignado.");
        }

        List<UUID> normalizedStudentIds = normalizeStudentIds(request.studentIds());
        List<AppUser> students = userRepository.findAllById(normalizedStudentIds);
        validateStudents(normalizedStudentIds, students);
        validateNoDuplicateStudentCase(clinicalCase.getId(), normalizedStudentIds);

        UUID sharedCourseId = resolveCourseIdForAssignment(normalizedStudentIds);
        LocalDateTime now = LocalDateTime.now();

        SimulationActivity activity = simulationActivityRepository.save(new SimulationActivity(
                UUID.randomUUID(),
                sharedCourseId,
                clinicalCase.getId(),
                clinicalCase.getTitulo(),
                clinicalCase.getDescripcion(),
                ACTIVITY_FORMATIVE,
                false,
                null,
                true,
                assignedBy,
                now
        ));

        List<SimulationSession> sessions = normalizedStudentIds.stream()
                .map(studentId -> new SimulationSession(
                        UUID.randomUUID(),
                        activity.getId(),
                        studentId,
                        SESSION_PENDING,
                        null,
                        now
                ))
                .toList();

        simulationSessionRepository.saveAll(sessions);

        return new CreateSimulationResponse(activity.getId(), clinicalCase.getId(), sessions.size());
    }

    @Transactional(readOnly = true)
    public List<StudentActivityResponse> getStudentActivities(UUID studentId) {
        return simulationSessionRepository.findStudentActivitiesByStudentId(studentId)
                .stream()
                .map(activity -> new StudentActivityResponse(
                        activity.getActivityId(),
                        activity.getClinicalCaseId(),
                        activity.getTitle(),
                        activity.getStatus()
                ))
                .toList();
    }

    private List<UUID> normalizeStudentIds(List<UUID> studentIds) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(studentIds);
        if (uniqueIds.size() != studentIds.size()) {
            throw new BadRequestException("La lista studentIds contiene duplicados.");
        }
        return List.copyOf(uniqueIds);
    }

    private void validateStudents(List<UUID> requestedIds, List<AppUser> users) {
        if (users.size() != requestedIds.size()) {
            Set<UUID> foundIds = users.stream().map(AppUser::getId).collect(java.util.stream.Collectors.toSet());
            List<UUID> missingIds = requestedIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new BadRequestException("No existen estudiantes para ids: " + missingIds);
        }

        List<UUID> invalidRoleIds = new ArrayList<>();
        for (AppUser user : users) {
            boolean isStudent = user.getRoles().stream().anyMatch(role -> role.getUserRole() == UserRole.ESTUDIANTE);
            if (!isStudent) {
                invalidRoleIds.add(user.getId());
            }
        }

        if (!invalidRoleIds.isEmpty()) {
            throw new BadRequestException("Los siguientes usuarios no tienen rol ESTUDIANTE: " + invalidRoleIds);
        }
    }

    private void validateNoDuplicateStudentCase(UUID clinicalCaseId, List<UUID> studentIds) {
        List<UUID> duplicatedStudentIds = simulationSessionRepository
                .findAssignedStudentIdsByClinicalCaseId(clinicalCaseId, studentIds);

        if (!duplicatedStudentIds.isEmpty()) {
            throw new ConflictException("Ya existen asignaciones para estudiante+caso: " + duplicatedStudentIds);
        }
    }

    private UUID resolveCourseIdForAssignment(List<UUID> studentIds) {
        List<UUID> sharedCourseIds = courseEnrollmentRepository
                .findSharedCourseIdsForStudents(studentIds, studentIds.size());

        if (!sharedCourseIds.isEmpty()) {
            return sharedCourseIds.getFirst();
        }

        List<UUID> studentCourseIds = courseEnrollmentRepository.findStudentCourseIds(studentIds);
        if (!studentCourseIds.isEmpty()) {
            return studentCourseIds.getFirst();
        }

        return courseEnrollmentRepository.findAnyCourseId()
                .orElseThrow(() -> new BadRequestException("No existe un curso disponible para crear la simulación."));
    }
}
