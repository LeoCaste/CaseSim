package cl.casesim.backend.simulations;

import cl.casesim.backend.auth.AppUser;
import cl.casesim.backend.auth.Role;
import cl.casesim.backend.auth.UserRepository;
import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseStatus;
import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.dto.CreateSimulationRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationAssignmentServiceTest {

    private final ClinicalCaseRepository clinicalCaseRepository = mock(ClinicalCaseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CourseEnrollmentRepository courseEnrollmentRepository = mock(CourseEnrollmentRepository.class);
    private final SimulationActivityRepository simulationActivityRepository = mock(SimulationActivityRepository.class);
    private final SimulationSessionRepository simulationSessionRepository = mock(SimulationSessionRepository.class);

    private final SimulationAssignmentService service = new SimulationAssignmentService(
            clinicalCaseRepository,
            userRepository,
            courseEnrollmentRepository,
            simulationActivityRepository,
            simulationSessionRepository
    );

    @Test
    void createSimulationShouldRejectDraftClinicalCase() {
        assertCaseStatusRejected(ClinicalCaseStatus.DRAFT);
    }

    @Test
    void createSimulationShouldRejectArchivedClinicalCase() {
        assertCaseStatusRejected(ClinicalCaseStatus.ARCHIVED);
    }

    @Test
    void createSimulationShouldAllowReadyClinicalCase() {
        UUID caseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(buildCase(caseId, ClinicalCaseStatus.READY)));
        when(userRepository.findAllById(List.of(studentId))).thenReturn(List.of(new AppUser(
                studentId,
                "Estudiante",
                "estudiante@casesim.cl",
                "hash",
                true,
                Set.of(new Role(UUID.randomUUID(), "ESTUDIANTE"))
        )));
        when(simulationSessionRepository.findAssignedStudentIdsByClinicalCaseId(caseId, List.of(studentId))).thenReturn(List.of());
        when(courseEnrollmentRepository.findSharedCourseIdsForStudents(List.of(studentId), 1)).thenReturn(List.of(courseId));
        when(simulationActivityRepository.save(any(SimulationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createSimulation(new CreateSimulationRequest(caseId, List.of(studentId)), professorId);

        assertEquals(caseId, response.clinicalCaseId());
        assertEquals(1, response.assignedStudents());
        verify(simulationSessionRepository).saveAll(any(List.class));
    }

    private void assertCaseStatusRejected(ClinicalCaseStatus status) {
        UUID caseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(buildCase(caseId, status)));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.createSimulation(new CreateSimulationRequest(caseId, List.of(studentId)), UUID.randomUUID()));

        assertEquals("Este caso aún no está listo para ser asignado.", exception.getMessage());
        verify(userRepository, never()).findAllById(any());
        verify(simulationActivityRepository, never()).save(any(SimulationActivity.class));
        verify(simulationSessionRepository, never()).saveAll(any());
    }

    private ClinicalCase buildCase(UUID caseId, ClinicalCaseStatus status) {
        return new ClinicalCase(
                caseId,
                "Caso listo",
                "desc",
                "Paciente",
                35,
                "F",
                "Dolor",
                "No tengo información asociada a eso.",
                status.isLegacyActive(),
                status,
                null,
                UUID.randomUUID(),
                LocalDateTime.now()
        );
    }
}
