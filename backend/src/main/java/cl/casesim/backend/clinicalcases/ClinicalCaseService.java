package cl.casesim.backend.clinicalcases;

import cl.casesim.backend.clinicalcases.dto.ClinicalCaseRequest;
import cl.casesim.backend.clinicalcases.dto.ClinicalCaseResponse;
import cl.casesim.backend.common.exception.BadRequestException;
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
    private static final String DRAFT_TITLE_PLACEHOLDER = "Borrador sin título";
    private static final String DRAFT_CHIEF_COMPLAINT_PLACEHOLDER = "Motivo de consulta pendiente.";
    private static final String DEFAULT_FACT_KEY = "general";
    private static final String DEFAULT_FACT_CATEGORY = "GENERAL";
    private static final int MIN_REVEAL_LEVEL = 1;
    private static final int MAX_REVEAL_LEVEL = 4;
    private static final int DEFAULT_REVEAL_LEVEL = 2;

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
        return clinicalCaseRepository.findByStatusNotOrderByCreadoEnDesc(ClinicalCaseStatus.ARCHIVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ClinicalCaseResponse getActiveClinicalCaseById(UUID id) {
        ClinicalCase clinicalCase = findActiveClinicalCaseByCaseOrActivityId(id);

        return toResponse(clinicalCase);
    }

    public ClinicalCaseResponse getActiveClinicalCaseByReference(String idReference) {
        UUID parsedId = tryParseUuid(idReference);
        if (parsedId != null) {
            return getActiveClinicalCaseById(parsedId);
        }

        // Compatibilidad mínima para flujo de asignación legado/mocks del frontend.
        if ("1".equals(idReference)) {
            ClinicalCase fallbackCase = clinicalCaseRepository.findByActivoTrueOrderByCreadoEnDesc()
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No existen casos clínicos activos."));
            return toResponse(fallbackCase);
        }

        throw new ResourceNotFoundException("Caso clínico no encontrado para id: " + idReference);
    }

    @Transactional
    public ClinicalCaseResponse createClinicalCase(ClinicalCaseRequest request, UUID authenticatedUserId) {
        LocalDateTime now = LocalDateTime.now();
        ClinicalCaseStatus status = resolveRequestedStatus(request);
        validateReadyMinimums(request, status);

        ClinicalCase clinicalCase = new ClinicalCase(
                UUID.randomUUID(),
                resolveRequiredStorageText(request.title(), DRAFT_TITLE_PLACEHOLDER),
                normalizeOptionalText(request.description()),
                normalizeOptionalText(request.patientName()),
                request.patientAge(),
                normalizeOptionalText(request.patientSex()),
                resolveRequiredStorageText(request.chiefComplaint(), DRAFT_CHIEF_COMPLAINT_PLACEHOLDER),
                resolveNoInformationPhrase(request.noInformationPhrase()),
                status.isLegacyActive(),
                status,
                request.estimatedTimeMinutes(),
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
        ClinicalCaseStatus status = resolveRequestedStatus(request);
        validateReadyMinimums(request, status);

        clinicalCase.actualizarDatos(
                resolveRequiredStorageText(request.title(), DRAFT_TITLE_PLACEHOLDER),
                normalizeOptionalText(request.description()),
                normalizeOptionalText(request.patientName()),
                request.patientAge(),
                normalizeOptionalText(request.patientSex()),
                resolveRequiredStorageText(request.chiefComplaint(), DRAFT_CHIEF_COMPLAINT_PLACEHOLDER),
                resolveNoInformationPhrase(request.noInformationPhrase()),
                status.isLegacyActive(),
                status,
                request.estimatedTimeMinutes()
        );

        ClinicalCase updatedClinicalCase = clinicalCaseRepository.save(clinicalCase);
        clinicalCaseFactRepository.deleteByCasoId(updatedClinicalCase.getId());
        saveFacts(updatedClinicalCase.getId(), request.facts());

        clinicalCasePersonalityRepository.deleteByCasoId(updatedClinicalCase.getId());
        savePersonality(updatedClinicalCase.getId(), request.personality());

        return toResponse(updatedClinicalCase);
    }

    @Transactional
    public void deleteClinicalCase(UUID idOrActivityId) {
        ClinicalCase clinicalCase = findClinicalCaseByCaseOrActivityId(idOrActivityId);

        simulationActivityRepository.deleteByCasoId(clinicalCase.getId());
        clinicalCaseRepository.deleteById(clinicalCase.getId());
    }

    private String resolveNoInformationPhrase(String noInformationPhrase) {
        String normalizedPhrase = normalizeOptionalText(noInformationPhrase);
        if (normalizedPhrase == null) {
            return DEFAULT_NO_INFORMATION_PHRASE;
        }
        return normalizedPhrase;
    }

    private ClinicalCaseStatus resolveRequestedStatus(ClinicalCaseRequest request) {
        if (request.status() != null) {
            return request.status();
        }
        if (request.active() != null) {
            return ClinicalCaseStatus.fromLegacyActive(request.active());
        }
        return ClinicalCaseStatus.READY;
    }

    private String resolveRequiredStorageText(String value, String placeholder) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? placeholder : normalized;
    }

    private void validateReadyMinimums(ClinicalCaseRequest request, ClinicalCaseStatus status) {
        if (status != ClinicalCaseStatus.READY) {
            return;
        }

        List<String> missingFields = new ArrayList<>();
        if (normalizeOptionalText(request.patientName()) == null) {
            missingFields.add("patientName");
        }
        if (request.patientAge() == null) {
            missingFields.add("patientAge");
        }
        if (normalizeOptionalText(request.patientSex()) == null) {
            missingFields.add("patientSex");
        }
        if (normalizeOptionalText(request.chiefComplaint()) == null) {
            missingFields.add("chiefComplaint");
        }
        if (request.facts() == null || request.facts().stream().noneMatch(this::hasValidFactContent)) {
            missingFields.add("facts");
        }
        if (resolveNoInformationPhrase(request.noInformationPhrase()) == null) {
            missingFields.add("noInformationPhrase");
        }

        if (!missingFields.isEmpty()) {
            throw new BadRequestException("El caso debe estar completo para quedar READY. Campos mínimos faltantes: " + missingFields);
        }
    }

    private boolean hasValidFactContent(ClinicalCaseRequest.ClinicalCaseFactRequest fact) {
        return fact != null && normalizeOptionalText(fact.content()) != null;
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
                        fact.getCategoria(),
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
                clinicalCase.getStatus(),
                clinicalCase.getDuracionEstimadaMinutos(),
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

            String factCategory = normalizeOptionalText(fact.category());
            if (factCategory == null) {
                factCategory = DEFAULT_FACT_CATEGORY;
            }

            String factContent = normalizeOptionalText(fact.content());
            if (factContent == null) {
                throw new BadRequestException("facts[" + order + "].content: el contenido del hecho clínico no puede estar vacío.");
            }

            int revealLevel = resolveRevealLevel(fact, order);

            ClinicalCaseFact entity = new ClinicalCaseFact(
                    UUID.randomUUID(),
                    caseId,
                    factCategory,
                    factKey,
                    factContent,
                    revealLevel,
                    serializeTriggers(parseIncomingTriggers(fact.triggers())),
                    false,
                    order
            );
            clinicalCaseFactRepository.save(entity);
            order++;
        }
    }

    private int resolveRevealLevel(ClinicalCaseRequest.ClinicalCaseFactRequest fact, int factIndex) {
        Integer explicitRevealLevel = fact.revealLevel();
        if (explicitRevealLevel != null) {
            if (explicitRevealLevel < MIN_REVEAL_LEVEL || explicitRevealLevel > MAX_REVEAL_LEVEL) {
                throw new BadRequestException("facts[" + factIndex + "].revealLevel: el nivel de revelación debe estar entre 1 y 4.");
            }
            return explicitRevealLevel;
        }

        String visibility = normalizeOptionalText(fact.visibility());
        if (visibility == null) {
            return DEFAULT_REVEAL_LEVEL;
        }

        String normalizedVisibility = visibility.toUpperCase();
        return switch (normalizedVisibility) {
            case "INITIAL", "INICIAL" -> 1;
            case "ON_QUESTION", "ONQUESTION", "BAJO_PREGUNTA", "BAJO PREGUNTA" -> 2;
            default -> throw new BadRequestException("facts[" + factIndex + "].visibility: valor inválido. use INITIAL u ON_QUESTION.");
        };
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

    private List<String> parseIncomingTriggers(Object triggersValue) {
        if (triggersValue == null) {
            return List.of();
        }

        if (triggersValue instanceof List<?> listValue) {
            return listValue.stream()
                    .map(item -> normalizeOptionalText(item == null ? null : item.toString()))
                    .filter(value -> value != null)
                    .toList();
        }

        if (triggersValue instanceof String stringValue) {
            String normalized = normalizeOptionalText(stringValue);
            if (normalized == null) {
                return List.of();
            }
            return List.of(normalized);
        }

        throw new BadRequestException("El campo triggers debe ser una lista de textos o un texto.");
    }

    private List<String> parseTriggers(Object triggersValue) {
        if (triggersValue == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        if (triggersValue instanceof List<?> listValue) {
            for (Object item : listValue) {
                String value = normalizeOptionalText(item == null ? null : item.toString());
                if (value != null) {
                    result.add(value);
                }
            }
            return result;
        }

        if (triggersValue instanceof java.util.Map<?, ?> mapValue) {
            Object keywords = mapValue.get("keywords");
            if (keywords instanceof List<?> keywordList) {
                for (Object item : keywordList) {
                    String value = normalizeOptionalText(item == null ? null : item.toString());
                    if (value != null) {
                        result.add(value);
                    }
                }
                return result;
            }
            return List.of();
        }

        String normalized = normalizeOptionalText(triggersValue.toString());
        if (normalized == null || normalized.equals("[]")) {
            return List.of();
        }

        if (normalized.startsWith("{") && normalized.contains("\"keywords\"")) {
            int start = normalized.indexOf('[');
            int end = normalized.lastIndexOf(']');
            if (start >= 0 && end > start) {
                normalized = normalized.substring(start, end + 1);
            }
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

    private ClinicalCase findClinicalCaseByCaseOrActivityId(UUID idOrActivityId) {
        return clinicalCaseRepository.findById(idOrActivityId)
                .or(() -> simulationActivityRepository.findById(idOrActivityId)
                        .flatMap(activity -> clinicalCaseRepository.findById(activity.getCasoId())))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Caso clínico no encontrado para id (caso/actividad): " + idOrActivityId));
    }

    private UUID tryParseUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
