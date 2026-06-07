package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseDescriptionParser;
import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonalityRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseSafetySanitizer;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static cl.casesim.backend.llm.TextNormalizationUtil.firstText;
import static cl.casesim.backend.llm.TextNormalizationUtil.safeFactPart;
import static cl.casesim.backend.llm.TextNormalizationUtil.safeMetadataValue;

public class ClinicalCasePromptContextAssembler {

    private final SimulationActivityRepository simulationActivityRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;
    private final ClinicalCasePersonalityRepository clinicalCasePersonalityRepository;

    public ClinicalCasePromptContextAssembler(
            SimulationActivityRepository simulationActivityRepository,
            ClinicalCaseRepository clinicalCaseRepository,
            ClinicalCasePersonalityRepository clinicalCasePersonalityRepository
    ) {
        this.simulationActivityRepository = simulationActivityRepository;
        this.clinicalCaseRepository = clinicalCaseRepository;
        this.clinicalCasePersonalityRepository = clinicalCasePersonalityRepository;
    }

    public PromptBuilderService.ClinicalPromptContext assemble(
            SimulationSession session,
            List<ClinicalCaseFact> selectedFacts
    ) {
        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId())
                .orElseThrow(() -> new ClinicalContextResolutionException("No existe actividad para la sesión " + session.getId()));

        ClinicalCase clinicalCase = clinicalCaseRepository.findById(activity.getCasoId())
                .orElseThrow(() -> new ClinicalContextResolutionException("No existe caso clínico para la actividad " + activity.getId()));

        return assembleResolved(session, clinicalCase, selectedFacts);
    }

    public PromptBuilderService.ClinicalPromptContext assemble(
            SimulationSession session,
            Function<UUID, List<ClinicalCaseFact>> selectedFactsProvider
    ) {
        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId())
                .orElseThrow(() -> new ClinicalContextResolutionException("No existe actividad para la sesión " + session.getId()));

        ClinicalCase clinicalCase = clinicalCaseRepository.findById(activity.getCasoId())
                .orElseThrow(() -> new ClinicalContextResolutionException("No existe caso clínico para la actividad " + activity.getId()));

        List<ClinicalCaseFact> selectedFacts = selectedFactsProvider == null
                ? List.of()
                : selectedFactsProvider.apply(clinicalCase.getId());

        return assembleResolved(session, clinicalCase, selectedFacts);
    }

    private PromptBuilderService.ClinicalPromptContext assembleResolved(
            SimulationSession session,
            ClinicalCase clinicalCase,
            List<ClinicalCaseFact> selectedFacts
    ) {
        List<String> facts = formatFacts(selectedFacts);

        List<String> personalityTraits = clinicalCasePersonalityRepository.findByCasoId(clinicalCase.getId())
                .stream()
                .map(personality -> personality.getRasgo() + ": " + personality.getDescripcion())
                .toList();

        var descriptionParts = ClinicalCaseDescriptionParser.parse(clinicalCase.getDescripcion());
        Map<String, String> safeMetadata = descriptionParts.legacyMetadata();
        String noInformationReply = resolveNoInformationReply(clinicalCase, safeMetadata);

        return new PromptBuilderService.ClinicalPromptContext(
                session.getId(),
                clinicalCase.getId(),
                ClinicalCaseSafetySanitizer.safeCaseTitle(),
                clinicalCase.getPacienteNombre(),
                clinicalCase.getPacienteEdad() == null ? null : String.valueOf(clinicalCase.getPacienteEdad()),
                clinicalCase.getPacienteSexo(),
                clinicalCase.getMotivoConsulta(),
                descriptionParts.clinicalContext(),
                noInformationReply,
                personalityTraits,
                facts,
                safeMetadataValue(safeMetadata, "initialMessage", "initial_message"),
                safeMetadataValue(safeMetadata, "context", "caseContext"),
                safeMetadataValue(safeMetadata, "currentIllness", "current_illness", "enfermedadActual"),
                safeMetadataValue(safeMetadata, "generalBackground", "general_background", "antecedentesGenerales"),
                safeMetadataValue(safeMetadata, "clinicalExam.findings", "clinicalExamFindings", "clinical_exam_findings", "findings"),
                safeMetadataValue(safeMetadata, "tone", "tono"),
                safeMetadataValue(safeMetadata, "detailLevel", "detail_level"),
                safeMetadataValue(safeMetadata, "behaviorGuidelines", "behavior_guidelines")
        );
    }

    public String resolveCaseNoInfoResponse(SimulationSession session) {
        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId()).orElse(null);
        if (activity == null) {
            return null;
        }
        ClinicalCase clinicalCase = clinicalCaseRepository.findById(activity.getCasoId()).orElse(null);
        if (clinicalCase == null) {
            return null;
        }
        var parts = ClinicalCaseDescriptionParser.parse(clinicalCase.getDescripcion());
        return resolveNoInformationReply(clinicalCase, parts.legacyMetadata());
    }

    public PromptBuilderService.ClinicalPromptContext emptyPromptContext(SimulationSession session) {
        return new PromptBuilderService.ClinicalPromptContext(
                session.getId(), null, null, null, null, null, null, null, null,
                List.of(), List.of(), null, null, null, null, null, null, null, null
        );
    }

    private List<String> formatFacts(List<ClinicalCaseFact> selectedFacts) {
        if (selectedFacts == null || selectedFacts.isEmpty()) {
            return List.of();
        }
        return selectedFacts.stream()
                .map(fact -> "[categoria=" + safeFactPart(fact.getCategoria()) + "] " + fact.getNombre() + ": " + fact.getContenidoPaciente())
                .toList();
    }

    private String resolveNoInformationReply(ClinicalCase clinicalCase, Map<String, String> safeMetadata) {
        return firstText(
                clinicalCase.getFraseSinInformacion(),
                safeMetadataValue(safeMetadata, "fallbackResponse", "fallback_response", "noInformationPhrase", "no_information_phrase")
        );
    }
}
