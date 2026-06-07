package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.clinicalcases.dto.ClinicalCaseRequest;
import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.times;

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

    @Test
    void getActiveClinicalCaseByReferenceShouldFallbackWhenReferenceIsOne() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = buildCase(caseId, creatorId);

        when(clinicalCaseRepository.findByActivoTrueOrderByCreadoEnDesc()).thenReturn(List.of(clinicalCase));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        var response = clinicalCaseService.getActiveClinicalCaseByReference("1");

        assertEquals(caseId, response.id());
        verify(clinicalCaseRepository).findByActivoTrueOrderByCreadoEnDesc();
    }

    @Test
    void getActiveClinicalCaseByReferenceShouldThrowNotFoundForInvalidNonLegacyReference() {
        assertThrows(ResourceNotFoundException.class,
                () -> clinicalCaseService.getActiveClinicalCaseByReference("abc"));
    }

    @Test
    void deleteClinicalCaseShouldDeleteActivitiesFirstAndThenClinicalCase() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = buildCase(caseId, creatorId);

        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(clinicalCase));
        doNothing().when(simulationActivityRepository).deleteByCasoId(caseId);

        clinicalCaseService.deleteClinicalCase(caseId);

        verify(simulationActivityRepository).deleteByCasoId(caseId);
        verify(clinicalCaseRepository).deleteById(caseId);
    }

    @Test
    void deleteClinicalCaseShouldThrowNotFoundWhenCaseDoesNotExist() {
        UUID caseId = UUID.randomUUID();

        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.empty());
        when(simulationActivityRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clinicalCaseService.deleteClinicalCase(caseId));

        verify(simulationActivityRepository, never()).deleteByCasoId(any(UUID.class));
        verify(clinicalCaseRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void updateClinicalCaseShouldSupportLegacyObjectTriggersAndNullTriggersInRequest() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = buildCase(caseId, creatorId);

        ClinicalCaseFact legacyFact = new ClinicalCaseFact(
                UUID.randomUUID(),
                caseId,
                "GENERAL",
                "legacy",
                "contenido",
                1,
                java.util.Map.of("keywords", List.of("inicio", "evolución")),
                false,
                0
        );

        when(clinicalCaseRepository.findByIdAndActivoTrue(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of(legacyFact));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso actualizado",
                "Descripción con [CASESIM_META]{\"foo\":\"bar\"}",
                "Paciente actualizado",
                35,
                "F",
                "Dolor torácico",
                "No recuerdo más detalles",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("GENERAL", "general", "dato", null, 1, null)),
                List.of("{\"meta\":true}")
        );

        var response = clinicalCaseService.updateClinicalCase(caseId, request);

        assertEquals("Caso actualizado", response.title());
        assertEquals("Descripción con [CASESIM_META]{\"foo\":\"bar\"}", response.description());
        assertEquals(List.of("inicio", "evolución"), response.facts().getFirst().triggers());
    }

    @Test
    void createClinicalCaseShouldPersistAntecedentesCategoryAndReturnIt() {
        UUID userId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> {
            ClinicalCase input = invocation.getArgument(0);
            return new ClinicalCase(
                    caseId,
                    input.getTitulo(),
                    input.getDescripcion(),
                    input.getPacienteNombre(),
                    input.getPacienteEdad(),
                    input.getPacienteSexo(),
                    input.getMotivoConsulta(),
                    input.getFraseSinInformacion(),
                    input.isActivo(),
                    input.getCreadoPor(),
                    input.getCreadoEn()
            );
        });

        ClinicalCaseFact fact = new ClinicalCaseFact(
                UUID.randomUUID(),
                caseId,
                "ANTECEDENTES",
                "antecedentes_personales",
                "HTA",
                1,
                "[\"hta\"]",
                false,
                0
        );
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of(fact));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso",
                "desc",
                "Paciente",
                40,
                "F",
                "Dolor",
                "No sé",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("ANTECEDENTES", "antecedentes_personales", "HTA", List.of("hta"), 1, null)),
                List.of()
        );

        var response = clinicalCaseService.createClinicalCase(request, userId);

        var captor = forClass(ClinicalCaseFact.class);
        verify(clinicalCaseFactRepository).save(captor.capture());
        assertEquals("ANTECEDENTES", captor.getValue().getCategoria());
        assertEquals("ANTECEDENTES", response.facts().getFirst().category());
        assertEquals("HTA", response.facts().getFirst().content());
    }


    @Test
    void updateClinicalCaseShouldPersistCompleteFactsAndReturnProfessorResponse() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = buildCase(caseId, creatorId);

        when(clinicalCaseRepository.findByIdAndActivoTrue(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of(new ClinicalCaseFact(
                UUID.randomUUID(),
                caseId,
                "HISTORIA_ACTUAL",
                "inicio_dolor",
                "El dolor inició ayer.",
                3,
                "[\"dolor\",\"inicio\"]",
                false,
                0
        )));

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso actualizado",
                "Descripción actualizada",
                "Paciente actualizado",
                35,
                "F",
                "Dolor torácico",
                "No recuerdo más detalles",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest(
                        "HISTORIA_ACTUAL",
                        "inicio_dolor",
                        "El dolor inició ayer.",
                        List.of("dolor", "inicio"),
                        3,
                        null
                )),
                List.of()
        );

        var response = clinicalCaseService.updateClinicalCase(caseId, request);

        var captor = forClass(ClinicalCaseFact.class);
        verify(clinicalCaseFactRepository).save(captor.capture());
        ClinicalCaseFact persisted = captor.getValue();
        assertEquals("HISTORIA_ACTUAL", persisted.getCategoria());
        assertEquals("inicio_dolor", persisted.getNombre());
        assertEquals("El dolor inició ayer.", persisted.getContenidoPaciente());
        assertEquals(3, persisted.getNivelRevelacion());
        assertEquals("[\"dolor\",\"inicio\"]", persisted.getTriggers());
        assertEquals("HISTORIA_ACTUAL", response.facts().getFirst().category());
        assertEquals(List.of("dolor", "inicio"), response.facts().getFirst().triggers());
        assertEquals(3, response.facts().getFirst().revealLevel());
    }

    @Test
    void createClinicalCaseShouldRejectFactWithoutContent() {
        UUID userId = UUID.randomUUID();

        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso",
                "desc",
                "Paciente",
                40,
                "F",
                "Dolor",
                "No sé",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("ANTECEDENTES", "antecedentes_personales", "   ", List.of("hta"), 1, null)),
                List.of()
        );

        assertThrows(BadRequestException.class, () -> clinicalCaseService.createClinicalCase(request, userId));
    }

    @Test
    void updateClinicalCaseShouldRejectFactWithoutContent() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = buildCase(caseId, creatorId);

        when(clinicalCaseRepository.findByIdAndActivoTrue(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso actualizado",
                "Descripción actualizada",
                "Paciente actualizado",
                35,
                "F",
                "Dolor torácico",
                "No recuerdo más detalles",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("ANTECEDENTES", "antecedentes", "", List.of("hta"), 1, null)),
                List.of()
        );

        assertThrows(BadRequestException.class, () -> clinicalCaseService.updateClinicalCase(caseId, request));
    }

    @Test
    void updateClinicalCaseShouldRejectInvalidTriggersObjectWithBadRequest() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = buildCase(caseId, creatorId);

        when(clinicalCaseRepository.findByIdAndActivoTrue(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso actualizado",
                "Descripción actualizada",
                "Paciente actualizado",
                35,
                "F",
                "Dolor torácico",
                "No recuerdo más detalles",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("ANTECEDENTES", "antecedentes", "HTA", java.util.Map.of("foo", "bar"), 1, null)),
                List.of()
        );

        assertThrows(BadRequestException.class, () -> clinicalCaseService.updateClinicalCase(caseId, request));
    }

    @Test
    void createClinicalCaseShouldPersistEightFactsSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> {
            ClinicalCase input = invocation.getArgument(0);
            return new ClinicalCase(
                    caseId,
                    input.getTitulo(),
                    input.getDescripcion(),
                    input.getPacienteNombre(),
                    input.getPacienteEdad(),
                    input.getPacienteSexo(),
                    input.getMotivoConsulta(),
                    input.getFraseSinInformacion(),
                    input.isActivo(),
                    input.getCreadoPor(),
                    input.getCreadoEn()
            );
        });

        List<ClinicalCaseFact> persistedFacts = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new ClinicalCaseFact(
                        UUID.randomUUID(),
                        caseId,
                        "GENERAL",
                        "fact-" + index,
                        "contenido-" + index,
                        1,
                        null,
                        false,
                        index
                ))
                .toList();
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(persistedFacts);
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        List<ClinicalCaseRequest.ClinicalCaseFactRequest> facts = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new ClinicalCaseRequest.ClinicalCaseFactRequest(
                        "GENERAL",
                        "fact-" + index,
                        "contenido-" + index,
                        List.of("trigger-" + index),
                        1,
                        null
                ))
                .toList();

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso de 8 hechos",
                "desc",
                "Paciente",
                40,
                "F",
                "Dolor",
                "No sé",
                true,
                facts,
                List.of()
        );

        var response = clinicalCaseService.createClinicalCase(request, userId);

        verify(clinicalCaseFactRepository, times(8)).save(any(ClinicalCaseFact.class));
        assertEquals(8, response.facts().size());
    }

    @Test
    void createClinicalCaseShouldAllowNullAndEmptyTriggers() {
        UUID userId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> {
            ClinicalCase input = invocation.getArgument(0);
            return new ClinicalCase(
                    caseId,
                    input.getTitulo(),
                    input.getDescripcion(),
                    input.getPacienteNombre(),
                    input.getPacienteEdad(),
                    input.getPacienteSexo(),
                    input.getMotivoConsulta(),
                    input.getFraseSinInformacion(),
                    input.isActivo(),
                    input.getCreadoPor(),
                    input.getCreadoEn()
            );
        });
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso",
                "desc",
                "Paciente",
                40,
                "F",
                "Dolor",
                "No sé",
                true,
                List.of(
                        new ClinicalCaseRequest.ClinicalCaseFactRequest("GENERAL", "f1", "dato1", null, 1, null),
                        new ClinicalCaseRequest.ClinicalCaseFactRequest("GENERAL", "f2", "dato2", List.of("  "), 1, null)
                ),
                List.of()
        );

        clinicalCaseService.createClinicalCase(request, userId);

        var captor = forClass(ClinicalCaseFact.class);
        verify(clinicalCaseFactRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(fact -> fact.getTriggers() == null));
    }

    @Test
    void createClinicalCaseShouldRejectUnexpectedTriggersType() {
        UUID userId = UUID.randomUUID();

        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso",
                "desc",
                "Paciente",
                40,
                "F",
                "Dolor",
                "No sé",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("ANTECEDENTES", "antecedentes", "HTA", java.util.Map.of("foo", "bar"), 1, null)),
                List.of()
        );

        assertThrows(BadRequestException.class, () -> clinicalCaseService.createClinicalCase(request, userId));
    }

    @Test
    void createClinicalCaseShouldMapVisibilityToRevealLevel() {
        UUID userId = UUID.randomUUID();

        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(any(UUID.class))).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(any(UUID.class))).thenReturn(List.of());

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso",
                "desc",
                "Paciente",
                40,
                "F",
                "Dolor",
                "No sé",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("GENERAL", "fact", "dato", "tos", null, "INITIAL")),
                List.of()
        );

        clinicalCaseService.createClinicalCase(request, userId);

        var captor = forClass(ClinicalCaseFact.class);
        verify(clinicalCaseFactRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getNivelRevelacion());
    }

    @Test
    void createClinicalCaseShouldDefaultRevealLevelWhenMissingVisibilityAndRevealLevel() {
        UUID userId = UUID.randomUUID();

        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(any(UUID.class))).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(any(UUID.class))).thenReturn(List.of());

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso",
                "desc",
                "Paciente",
                40,
                "F",
                "Dolor",
                "No sé",
                true,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("GENERAL", "fact", "dato", "tos", null, null)),
                List.of()
        );

        clinicalCaseService.createClinicalCase(request, userId);

        var captor = forClass(ClinicalCaseFact.class);
        verify(clinicalCaseFactRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getNivelRevelacion());
    }

    @Test
    void createClinicalCaseShouldAllowIncompleteDraftAndExposeStatus() {
        UUID userId = UUID.randomUUID();
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(any(UUID.class))).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(any(UUID.class))).thenReturn(List.of());

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ClinicalCaseStatus.DRAFT,
                List.of(),
                List.of()
        );

        var response = clinicalCaseService.createClinicalCase(request, userId);

        assertEquals(ClinicalCaseStatus.DRAFT, response.status());
        assertTrue(response.active());
        verify(clinicalCaseRepository).save(any(ClinicalCase.class));
    }

    @Test
    void updateClinicalCaseShouldAllowChangingDraftToReadyWhenMinimumsExist() {
        UUID caseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ClinicalCase clinicalCase = new ClinicalCase(
                caseId,
                "Borrador",
                null,
                null,
                null,
                null,
                "Motivo pendiente",
                "No tengo información asociada a eso.",
                true,
                ClinicalCaseStatus.DRAFT,
                creatorId,
                LocalDateTime.now()
        );

        when(clinicalCaseRepository.findByIdAndActivoTrue(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseRepository.save(any(ClinicalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId)).thenReturn(List.of());
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        var response = clinicalCaseService.updateClinicalCase(caseId, new ClinicalCaseRequest(
                "Caso listo",
                "desc",
                "Paciente",
                45,
                "F",
                "Dolor",
                "No tengo información",
                null,
                ClinicalCaseStatus.READY,
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("GENERAL", "dato", "Contenido", null, 1, null)),
                List.of()
        ));

        assertEquals(ClinicalCaseStatus.READY, response.status());
        assertTrue(response.active());
    }

    @Test
    void createClinicalCaseShouldRejectReadyWhenMinimumsAreMissing() {
        UUID userId = UUID.randomUUID();

        ClinicalCaseRequest request = new ClinicalCaseRequest(
                "Caso incompleto",
                null,
                null,
                null,
                "F",
                "Dolor",
                null,
                null,
                ClinicalCaseStatus.READY,
                List.of(),
                List.of()
        );

        assertThrows(BadRequestException.class, () -> clinicalCaseService.createClinicalCase(request, userId));
        verify(clinicalCaseRepository, never()).save(any(ClinicalCase.class));
    }

    @Test
    void clinicalCaseShouldMapLegacyActiveWhenStatusIsMissing() {
        UUID caseId = UUID.randomUUID();
        ClinicalCase legacyInactive = new ClinicalCase(
                caseId,
                "Legacy",
                null,
                "Paciente",
                50,
                "M",
                "Dolor",
                "No tengo información asociada a eso.",
                false,
                UUID.randomUUID(),
                LocalDateTime.now()
        );

        assertEquals(ClinicalCaseStatus.DRAFT, legacyInactive.getStatus());
    }

    // Tests for estimatedTimeMinutes were removed along with the field.
    // Validation of estimatedTimeMinutes (@Min/@Max) was removed.

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
                List.of(new ClinicalCaseRequest.ClinicalCaseFactRequest("GENERAL", "general", "Dato clínico", null, 1, null)),
                List.of()
        );
    }
}
