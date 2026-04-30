package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.clinicalcases.dto.ClinicalCaseRequest;
import cl.casesim.backend.clinicalcases.dto.ClinicalCaseResponse;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClinicalCaseService {

    private static final String DEFAULT_NO_INFORMATION_PHRASE = "No tengo información asociada a eso.";
    private static final String DEFAULT_FACT_KEY = "general";
    private static final String DEFAULT_FACT_CATEGORY = "GENERAL";

    private final ClinicalCaseRepository clinicalCaseRepository;
    private final ClinicalCaseFactRepository clinicalCaseFactRepository;
    private final ClinicalCasePersonalityRepository clinicalCasePersonalityRepository;
    private final SimulationActivityRepository simulationActivityRepository;

    public ClinicalCaseService(
            ClinicalCaseRepository clinicalCaseRepository,
            ClinicalCaseFactRepository clinicalCaseFactRepository,
            ClinicalCasePersonalityRepository clinicalCasePersonalityRepository,
            SimulationActivityRepository simulationActivityRepository
    ) {
        this.clinicalCaseRepository = clinicalCaseRepository;
        this.clinicalCaseFactRepository = clinicalCaseFactRepository;
        this.clinicalCasePersonalityRepository = clinicalCasePersonalityRepository;
        this.simulationActivityRepository = simulationActivityRepository;
    }

    public List<ClinicalCaseResponse> getActiveClinicalCases() {
        return clinicalCaseRepository.findByActivoTrueOrderByCreadoEnDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ClinicalCaseResponse getActiveClinicalCaseById(UUID id) {
        ClinicalCase clinicalCase = findActiveClinicalCaseByCaseOrActivityId(id);

        return toResponse(clinicalCase);
    }

    @Transactional
    public ClinicalCaseResponse createClinicalCase(ClinicalCaseRequest request, UUID authenticatedUserId) {
        LocalDateTime now = LocalDateTime.now();

        ClinicalCase clinicalCase = new ClinicalCase(
                UUID.randomUUID(),
                request.title().trim(),
                normalizeOptionalText(request.description()),
                normalizeOptionalText(request.patientName()),
                request.patientAge(),
                normalizeOptionalText(request.patientSex()),
                request.chiefComplaint().trim(),
                resolveNoInformationPhrase(request.noInformationPhrase()),
                request.active() == null || request.active(),
                authenticatedUserId,
                now
        );

        ClinicalCase savedClinicalCase = clinicalCaseRepository.save(clinicalCase);
        saveFacts(savedClinicalCase.getId(), request.facts());
        savePersonality(savedClinicalCase.getId(), request.personality());

        return toResponse(savedClinicalCase);
    }

    @Transactional
    public ClinicalCaseResponse updateClinicalCase(UUID id, ClinicalCaseRequest request) {
        ClinicalCase clinicalCase = findActiveClinicalCaseByCaseOrActivityId(id);

        clinicalCase.actualizarDatos(
                request.title().trim(),
                normalizeOptionalText(request.description()),
                normalizeOptionalText(request.patientName()),
                request.patientAge(),
                normalizeOptionalText(request.patientSex()),
                request.chiefComplaint().trim(),
                resolveNoInformationPhrase(request.noInformationPhrase()),
                request.active() == null || request.active()
        );

        ClinicalCase updatedClinicalCase = clinicalCaseRepository.save(clinicalCase);
        clinicalCaseFactRepository.deleteByCasoId(updatedClinicalCase.getId());
        saveFacts(updatedClinicalCase.getId(), request.facts());

        clinicalCasePersonalityRepository.deleteByCasoId(updatedClinicalCase.getId());
        savePersonality(updatedClinicalCase.getId(), request.personality());

        return toResponse(updatedClinicalCase);
    }

    @Transactional
    public void deactivateClinicalCase(UUID id) {
        ClinicalCase clinicalCase = clinicalCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caso clínico no encontrado con id: " + id));

        clinicalCase.desactivar();
        clinicalCaseRepository.save(clinicalCase);
    }

    private String resolveNoInformationPhrase(String noInformationPhrase) {
        String normalizedPhrase = normalizeOptionalText(noInformationPhrase);
        if (normalizedPhrase == null) {
            return DEFAULT_NO_INFORMATION_PHRASE;
        }
        return normalizedPhrase;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            return null;
        }

        return normalizedValue;
    }

    private ClinicalCaseResponse toResponse(ClinicalCase clinicalCase) {
        List<ClinicalCaseResponse.ClinicalCaseFactResponse> facts = clinicalCaseFactRepository
                .findByCasoIdOrderByOrdenAsc(clinicalCase.getId())
                .stream()
                .map(fact -> new ClinicalCaseResponse.ClinicalCaseFactResponse(
                        fact.getNombre(),
                        fact.getContenidoPaciente(),
                        parseTriggers(fact.getTriggers()),
                        fact.getNivelRevelacion()
                ))
                .toList();

        List<String> personality = clinicalCasePersonalityRepository
                .findByCasoId(clinicalCase.getId())
                .stream()
                .map(ClinicalCasePersonality::getRasgo)
                .toList();

        return new ClinicalCaseResponse(
                clinicalCase.getId(),
                clinicalCase.getTitulo(),
                clinicalCase.getDescripcion(),
                clinicalCase.getPacienteNombre(),
                clinicalCase.getPacienteEdad(),
                clinicalCase.getPacienteSexo(),
                clinicalCase.getMotivoConsulta(),
                clinicalCase.getFraseSinInformacion(),
                clinicalCase.isActivo(),
                clinicalCase.getCreadoEn(),
                facts,
                personality
        );
    }

    private void saveFacts(UUID caseId, List<ClinicalCaseRequest.ClinicalCaseFactRequest> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }

        int order = 0;
        for (ClinicalCaseRequest.ClinicalCaseFactRequest fact : facts) {
            if (fact == null) {
                continue;
            }

            String factKey = normalizeOptionalText(fact.key());
            if (factKey == null) {
                factKey = DEFAULT_FACT_KEY;
            }

            ClinicalCaseFact entity = new ClinicalCaseFact(
                    UUID.randomUUID(),
                    caseId,
                    DEFAULT_FACT_CATEGORY,
                    factKey,
                    fact.content().trim(),
                    fact.revealLevel(),
                    serializeTriggers(fact.triggers()),
                    false,
                    order
            );
            clinicalCaseFactRepository.save(entity);
            order++;
        }
    }

    private void savePersonality(UUID caseId, List<String> personality) {
        if (personality == null || personality.isEmpty()) {
            return;
        }

        for (String trait : personality) {
            String normalizedTrait = normalizeOptionalText(trait);
            if (normalizedTrait == null) {
                continue;
            }

            ClinicalCasePersonality entity = new ClinicalCasePersonality(
                    UUID.randomUUID(),
                    caseId,
                    normalizedTrait,
                    normalizedTrait
            );
            clinicalCasePersonalityRepository.save(entity);
        }
    }

    private String serializeTriggers(List<String> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return null;
        }

        List<String> normalized = triggers.stream()
                .map(this::normalizeOptionalText)
                .filter(value -> value != null)
                .distinct()
                .toList();

        if (normalized.isEmpty()) {
            return null;
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < normalized.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(escapeJson(normalized.get(i))).append('"');
        }
        json.append(']');
        return json.toString();
    }

    private List<String> parseTriggers(String triggersJson) {
        String normalized = normalizeOptionalText(triggersJson);
        if (normalized == null || normalized.equals("[]")) {
            return List.of();
        }

        String content = normalized;
        if (content.startsWith("[")) {
            content = content.substring(1);
        }
        if (content.endsWith("]")) {
            content = content.substring(0, content.length() - 1);
        }

        if (content.isBlank()) {
            return List.of();
        }

        String[] tokens = content.split(",");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String value = token.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            value = value.replace("\\\"", "\"").replace("\\\\", "\\");
            value = normalizeOptionalText(value);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ClinicalCase findActiveClinicalCaseByCaseOrActivityId(UUID idOrActivityId) {
        return clinicalCaseRepository.findByIdAndActivoTrue(idOrActivityId)
                .or(() -> simulationActivityRepository.findById(idOrActivityId)
                        .flatMap(activity -> clinicalCaseRepository.findByIdAndActivoTrue(activity.getCasoId())))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Caso clínico no encontrado para id (caso/actividad): " + idOrActivityId));
    }
}
