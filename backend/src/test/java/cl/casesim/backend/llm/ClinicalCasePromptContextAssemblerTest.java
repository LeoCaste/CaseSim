package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonality;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonalityRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicalCasePromptContextAssemblerTest {

    private final SimulationActivityRepository simulationActivityRepository = mock(SimulationActivityRepository.class);
    private final ClinicalCaseRepository clinicalCaseRepository = mock(ClinicalCaseRepository.class);
    private final ClinicalCasePersonalityRepository clinicalCasePersonalityRepository = mock(ClinicalCasePersonalityRepository.class);

    private ClinicalCasePromptContextAssembler assembler;
    private SimulationSession session;
    private SimulationActivity activity;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        assembler = new ClinicalCasePromptContextAssembler(
                simulationActivityRepository,
                clinicalCaseRepository,
                clinicalCasePersonalityRepository
        );
        UUID activityId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        session = new SimulationSession(
                UUID.randomUUID(),
                activityId,
                UUID.randomUUID(),
                "EN_CURSO",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        activity = new SimulationActivity(
                activityId,
                UUID.randomUUID(),
                caseId,
                "Actividad",
                null,
                "FORMATIVO",
                false,
                null,
                true,
                UUID.randomUUID(),
                LocalDateTime.now()
        );
    }

    @Test
    void armaContextoConDatosDirectosDelCasoYFactsFormateados() {
        ClinicalCase clinicalCase = clinicalCase("Historia comunicable", "No sé eso");
        ClinicalCaseFact fact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "Síntomas", "dolor", "Me duele el abdomen", 1, null, false, 0);
        when(simulationActivityRepository.findById(session.getActividadId())).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(clinicalCase));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of(
                new ClinicalCasePersonality(UUID.randomUUID(), caseId, "Ansioso", "responde con preocupación")
        ));

        PromptBuilderService.ClinicalPromptContext context = assembler.assemble(session, List.of(fact));

        assertEquals(session.getId(), context.sessionId());
        assertEquals(caseId, context.clinicalCaseId());
        assertEquals("Paciente", context.patientName());
        assertEquals("24", context.patientAge());
        assertEquals("F", context.patientSex());
        assertEquals("Dolor abdominal", context.chiefComplaint());
        assertEquals("Historia comunicable", context.caseHistory());
        assertEquals("No sé eso", context.noInformationReply());
        assertEquals(List.of("[categoria=Síntomas] dolor: Me duele el abdomen"), context.facts());
        assertEquals(List.of("Ansioso: responde con preocupación"), context.personalityTraits());
    }

    @Test
    void extraeMetadataAllowlistYExcluyeExpectedDiagnosisYMetaCrudo() {
        String description = """
                Historia clínica visible.
                expectedDiagnosis: Apendicitis
                [CASESIM_META]
                initialMessage: Hola soy Paciente
                context: vive con su madre
                currentIllness: dolor desde ayer
                generalBackground: sin antecedentes
                clinicalExam.findings: abdomen sensible
                tone: preocupado
                detailLevel: breve
                behaviorGuidelines: responder lento
                fallbackResponse: No recuerdo ese dato
                expectedDiagnosis: No debe salir
                """;
        when(simulationActivityRepository.findById(session.getActividadId())).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(clinicalCase(description, null)));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        PromptBuilderService.ClinicalPromptContext context = assembler.assemble(session, List.of());

        assertEquals("Hola soy Paciente", context.initialMessage());
        assertEquals("vive con su madre", context.broaderContext());
        assertEquals("dolor desde ayer", context.currentIllness());
        assertEquals("sin antecedentes", context.generalBackground());
        assertEquals("abdomen sensible", context.clinicalExamFindings());
        assertEquals("preocupado", context.tone());
        assertEquals("breve", context.detailLevel());
        assertEquals("responder lento", context.behaviorGuidelines());
        assertEquals("No recuerdo ese dato", context.noInformationReply());
        assertFalse(context.caseHistory().contains("[CASESIM_META]"));
        assertFalse(context.caseHistory().contains("expectedDiagnosis"));
        assertFalse(context.caseHistory().contains("Apendicitis"));
    }

    @Test
    void prioridadNoInfoCasoSobreMetadataFallbackResponse() {
        when(simulationActivityRepository.findById(session.getActividadId())).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(clinicalCase("""
                Historia.
                [CASESIM_META]
                fallbackResponse: Respuesta metadata
                """, "Respuesta caso")));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        PromptBuilderService.ClinicalPromptContext context = assembler.assemble(session, List.of());

        assertEquals("Respuesta caso", context.noInformationReply());
        assertEquals("Respuesta caso", assembler.resolveCaseNoInfoResponse(session));
    }

    @Test
    void manejaCasoSinMetadata() {
        when(simulationActivityRepository.findById(session.getActividadId())).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(clinicalCase("Historia sin metadata", null)));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        PromptBuilderService.ClinicalPromptContext context = assembler.assemble(session, List.of());

        assertEquals("Historia sin metadata", context.caseHistory());
        assertNull(context.initialMessage());
        assertNull(context.noInformationReply());
    }

    @Test
    void manejaPersonalidadVacia() {
        when(simulationActivityRepository.findById(session.getActividadId())).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(clinicalCase("Historia", null)));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId)).thenReturn(List.of());

        PromptBuilderService.ClinicalPromptContext context = assembler.assemble(session, List.of());

        assertNotNull(context.personalityTraits());
        assertTrue(context.personalityTraits().isEmpty());
    }

    @Test
    void lanzaErrorDeContextoSiFaltaActividad() {
        when(simulationActivityRepository.findById(session.getActividadId())).thenReturn(Optional.empty());

        assertThrows(ClinicalContextResolutionException.class, () -> assembler.assemble(session, List.of()));
        assertNull(assembler.resolveCaseNoInfoResponse(session));
    }

    @Test
    void lanzaErrorDeContextoSiFaltaCaso() {
        when(simulationActivityRepository.findById(session.getActividadId())).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThrows(ClinicalContextResolutionException.class, () -> assembler.assemble(session, List.of()));
        assertNull(assembler.resolveCaseNoInfoResponse(session));
    }

    private ClinicalCase clinicalCase(String description, String noInfoPhrase) {
        return new ClinicalCase(
                caseId,
                "Caso",
                description,
                "Paciente",
                24,
                "F",
                "Dolor abdominal",
                noInfoPhrase,
                true,
                UUID.randomUUID(),
                LocalDateTime.now()
        );
    }
}
