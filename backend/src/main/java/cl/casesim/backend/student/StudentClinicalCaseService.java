package cl.casesim.backend.student;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseSafetySanitizer;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.student.dto.StudentClinicalCaseResponse;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StudentClinicalCaseService {

    private final SimulationSessionRepository simulationSessionRepository;
    private final SimulationActivityRepository simulationActivityRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;

    public StudentClinicalCaseService(
            SimulationSessionRepository simulationSessionRepository,
            SimulationActivityRepository simulationActivityRepository,
            ClinicalCaseRepository clinicalCaseRepository
    ) {
        this.simulationSessionRepository = simulationSessionRepository;
        this.simulationActivityRepository = simulationActivityRepository;
        this.clinicalCaseRepository = clinicalCaseRepository;
    }

    @Transactional(readOnly = true)
    public StudentClinicalCaseResponse getAssignedClinicalCase(UUID activityId, UUID studentId) {
        SimulationSession session = simulationSessionRepository.findByActividadIdAndEstudianteId(activityId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Actividad clínica no encontrada para el estudiante autenticado."));

        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId())
                .orElseThrow(() -> new ResourceNotFoundException("Actividad clínica no encontrada."));

        ClinicalCase clinicalCase = clinicalCaseRepository.findByIdAndActivoTrue(activity.getCasoId())
                .orElseThrow(() -> new ResourceNotFoundException("Caso clínico no encontrado o inactivo."));

        return new StudentClinicalCaseResponse(
                activity.getId(),
                clinicalCase.getId(),
                ClinicalCaseSafetySanitizer.safeCaseTitle(),
                clinicalCase.getPacienteNombre(),
                clinicalCase.getPacienteEdad(),
                clinicalCase.getPacienteSexo(),
                clinicalCase.getMotivoConsulta()
        );
    }
}
