package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.clinicalcases.dto.ClinicalCaseRequest;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClinicalCaseServiceTest {

    private final ClinicalCaseRepository clinicalCaseRepository = mock(ClinicalCaseRepository.class);
    private final ClinicalCaseFactRepository clinicalCaseFactRepository = mock(ClinicalCaseFactRepository.class);
    private final ClinicalCasePersonalityRepository clinicalCasePersonalityRepository = mock(ClinicalCasePersonalityRepository.class);
    private final SimulationActivityRepository simulationActivityRepository = mock(SimulationActivityRepository.class);

    private final ClinicalCaseService clinicalCaseService = new ClinicalCaseService(
            clinicalCaseRepository,
            clinicalCaseFactRepository,
            clinicalCasePersonalityRepository,
            simulationActivityRepository
    );

    @Test
    void updateClinicalCaseShouldPersistChangesWithoutOwnershipValidation() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = buildCase(caseId, creatorId);

        when(clinicalCaseRepository.existsById(caseId)).thenReturn(true);
        when(clinicalCaseRepository.findByIdAndActivoTrue(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        var response = clinicalCaseService.updateClinicalCase(caseId, buildRequest());

        verify(clinicalCaseRepository).save(any(ClinicalCase.class));
        assertEquals("Caso actualizado", response.title());
        assertEquals("Paciente actualizado", response.patientName());
        assertEquals(35, response.patientAge());
    }

    @Test
    void updateClinicalCaseShouldResolveActivityIdToClinicalCaseId() {
        UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID activityId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID creatorId = UUID.randomUUID();

        ClinicalCase clinicalCase = buildCase(caseId, creatorId);
        SimulationActivity activity = new SimulationActivity(
                activityId,
                UUID.randomUUID(),
                caseId,
                "Actividad demo",
                "desc",
                "FORMATIVO",
                false,
                null,
                true,
                creatorId,
                LocalDateTime.now()
        );

        when(clinicalCaseRepository.findByIdAndActivoTrue(activityId)).thenReturn(Optional.empty());
        when(simulationActivityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findByIdAndActivoTrue(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        var response = clinicalCaseService.updateClinicalCase(activityId, buildRequest());

        verify(simulationActivityRepository).findById(activityId);
        verify(clinicalCaseRepository).findByIdAndActivoTrue(caseId);
        assertEquals(caseId, response.id());
        assertEquals("Caso actualizado", response.title());
    }

    private ClinicalCase buildCase(UUID caseId, UUID creatorId) {
        return new ClinicalCase(
                caseId,
                "Caso demo",
                "Descripción",
                "Paciente",
                34,
                "F",
                "Cefalea",
                "No tengo información asociada a eso.",
                true,
                creatorId,
                LocalDateTime.now()
        );
    }

    private ClinicalCaseRequest buildRequest() {
        return new ClinicalCaseRequest(
                "Caso actualizado",
                "Descripción actualizada",
                "Paciente actualizado",
                35,
                "F",
                "Dolor torácico",
                "No recuerdo más detalles",
                true,
                List.of(),
                List.of()
        );
    }
}
