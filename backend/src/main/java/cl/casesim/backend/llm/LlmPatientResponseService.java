package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonalityRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.ChatMessageRepository;
import cl.casesim.backend.sessions.MockPatientResponseService;
import cl.casesim.backend.sessions.PatientResponseService;
import cl.casesim.backend.sessions.SessionRevealedFact;
import cl.casesim.backend.sessions.SessionRevealedFactRepository;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class LlmPatientResponseService implements PatientResponseService {

    private static final String LLM_ERROR_FALLBACK = "Entiendo. Cuénteme un poco más sobre eso.";

    private final LlmProperties llmProperties;
    private final LlmClient llmClient;
    private final PromptBuilderService promptBuilderService;
    private final ResponseSafetyFilter responseSafetyFilter;
    private final ChatMessageRepository chatMessageRepository;
    private final MockPatientResponseService mockPatientResponseService;
    private final LlmUsageService llmUsageService;
    private final SimulationActivityRepository simulationActivityRepository;
    private final SimulationSessionRepository simulationSessionRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;
    private final ClinicalCaseFactRepository clinicalCaseFactRepository;
    private final ClinicalCasePersonalityRepository clinicalCasePersonalityRepository;
    private final SessionRevealedFactRepository sessionRevealedFactRepository;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N} ]");
    private static final List<String> LEVEL_2_HINTS = List.of("que", "como", "donde", "cuando", "cuanto", "tiene", "siente", "dolor", "fiebre", "tos", "sintoma", "vomito", "nausea");
    private static final List<String> LEVEL_3_HINTS = List.of("desde", "antecedente", "alergia", "medicamento", "cirugia", "hospital", "laboratorio", "examen", "resultado", "familiar", "cronico", "tratamiento");

    public LlmPatientResponseService(
            LlmProperties llmProperties,
            LlmClient llmClient,
            PromptBuilderService promptBuilderService,
            ResponseSafetyFilter responseSafetyFilter,
            ChatMessageRepository chatMessageRepository,
            MockPatientResponseService mockPatientResponseService,
            LlmUsageService llmUsageService,
            SimulationActivityRepository simulationActivityRepository,
            SimulationSessionRepository simulationSessionRepository,
            ClinicalCaseRepository clinicalCaseRepository,
            ClinicalCaseFactRepository clinicalCaseFactRepository,
            ClinicalCasePersonalityRepository clinicalCasePersonalityRepository,
            SessionRevealedFactRepository sessionRevealedFactRepository
    ) {
        this.llmProperties = llmProperties;
        this.llmClient = llmClient;
        this.promptBuilderService = promptBuilderService;
        this.responseSafetyFilter = responseSafetyFilter;
        this.chatMessageRepository = chatMessageRepository;
        this.mockPatientResponseService = mockPatientResponseService;
        this.llmUsageService = llmUsageService;
        this.simulationActivityRepository = simulationActivityRepository;
        this.simulationSessionRepository = simulationSessionRepository;
        this.clinicalCaseRepository = clinicalCaseRepository;
        this.clinicalCaseFactRepository = clinicalCaseFactRepository;
        this.clinicalCasePersonalityRepository = clinicalCasePersonalityRepository;
        this.sessionRevealedFactRepository = sessionRevealedFactRepository;
    }

    @Override
    public String generateResponse(SimulationSession session, String userMessage) {
        long startedAt = System.currentTimeMillis();
        int estimatedPromptTokens = llmUsageService.estimateTokens(userMessage);
        String resolvedModel = llmProperties.getModel();
        String resolvedProvider = llmProperties.getProvider();

        if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
            String fallback = mockPatientResponseService.generateResponse(session, userMessage);
            llmUsageService.registerCall(
                    session.getId(),
                    resolvedProvider,
                    resolvedModel,
                    estimatedPromptTokens,
                    llmUsageService.estimateTokens(fallback),
                    (int) (System.currentTimeMillis() - startedAt),
                    true,
                    "LLM deshabilitado o API key no configurada."
            );
            return fallback;
        }

        try {
            List<ChatMessage> history = loadRecentHistory(session.getId());
            PromptBuilderService.ClinicalPromptContext context = buildPromptContext(session, userMessage);

            List<LlmClient.ChatPromptMessage> promptMessages = promptBuilderService.buildMessages(
                    context,
                    history,
                    userMessage,
                    new PromptBuilderService.PatientBehaviorConfig(
                            llmProperties.getSystemPrompt(),
                            llmProperties.getPatientBehaviorRules(),
                            resolveNoInfoResponse(context.noInformationReply()),
                            llmProperties.getRevealStrategy()
                    )
            );

            estimatedPromptTokens = promptMessages.stream()
                    .map(LlmClient.ChatPromptMessage::content)
                    .mapToInt(llmUsageService::estimateTokens)
                    .sum();

            String llmResponse = llmClient.generateChatCompletion(
                    promptMessages,
                    llmProperties.getTemperature(),
                    llmProperties.getMaxTokens()
            );
            String safeResponse = responseSafetyFilter.applyOrFallback(
                    llmResponse,
                    llmProperties.isEnabledSafetyFilter()
            );
            boolean fallbackUsed = ResponseSafetyFilter.SAFE_FALLBACK.equals(safeResponse);

            llmUsageService.registerCall(
                    session.getId(),
                    resolvedProvider,
                    resolvedModel,
                    estimatedPromptTokens,
                    llmUsageService.estimateTokens(safeResponse),
                    (int) (System.currentTimeMillis() - startedAt),
                    fallbackUsed,
                    null
            );

            return safeResponse;
        } catch (RuntimeException ex) {
            String fallback = responseSafetyFilter.applyOrFallback(LLM_ERROR_FALLBACK);
            llmUsageService.registerCall(
                    session.getId(),
                    resolvedProvider,
                    resolvedModel,
                    estimatedPromptTokens,
                    llmUsageService.estimateTokens(fallback),
                    (int) (System.currentTimeMillis() - startedAt),
                    true,
                    ex.getMessage()
            );
            return fallback;
        }
    }

    private List<ChatMessage> loadRecentHistory(java.util.UUID sessionId) {
        int historyTurns = Math.max(0, llmProperties.getMaxHistoryMessages());
        if (historyTurns == 0) {
            return List.of();
        }

        return chatMessageRepository
                .findBySesionIdOrderByNumeroTurnoDesc(sessionId, PageRequest.of(0, historyTurns))
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getNumeroTurno))
                .toList();
    }

    private PromptBuilderService.ClinicalPromptContext buildPromptContext(SimulationSession session, String userMessage) {
        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId()).orElse(null);
        if (activity == null) {
            return emptyPromptContext(session);
        }

        ClinicalCase clinicalCase = clinicalCaseRepository.findById(activity.getCasoId()).orElse(null);
        if (clinicalCase == null) {
            return emptyPromptContext(session);
        }

        List<ClinicalCaseFact> allFacts = clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(clinicalCase.getId());
        List<ClinicalCaseFact> selectedFacts = selectFactsForPrompt(session.getId(), userMessage, allFacts);

        List<String> facts = selectedFacts
                .stream()
                .map(fact -> fact.getNombre() + ": " + fact.getContenidoPaciente())
                .toList();

        List<String> personalityTraits = clinicalCasePersonalityRepository.findByCasoId(clinicalCase.getId())
                .stream()
                .map(personality -> personality.getRasgo() + ": " + personality.getDescripcion())
                .toList();

        return new PromptBuilderService.ClinicalPromptContext(
                session.getId(),
                clinicalCase.getMotivoConsulta(),
                clinicalCase.getDescripcion(),
                clinicalCase.getFraseSinInformacion(),
                personalityTraits,
                facts
        );
    }

    private PromptBuilderService.ClinicalPromptContext emptyPromptContext(SimulationSession session) {
        return new PromptBuilderService.ClinicalPromptContext(
                session.getId(),
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    List<ClinicalCaseFact> selectFactsForPrompt(UUID sessionId, String userMessage) {
        SimulationSession session = simulationSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return List.of();
        }

        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId()).orElse(null);
        if (activity == null) {
            return List.of();
        }

        List<ClinicalCaseFact> allFacts = clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(activity.getCasoId());
        return selectFactsForPrompt(sessionId, userMessage, allFacts);
    }

    private List<ClinicalCaseFact> selectFactsForPrompt(UUID sessionId, String userMessage, List<ClinicalCaseFact> allFacts) {
        if (allFacts == null || allFacts.isEmpty()) {
            return List.of();
        }

        Set<UUID> alreadyRevealedIds = new HashSet<>(sessionRevealedFactRepository.findFactIdsBySessionId(sessionId));
        Set<String> messageKeywords = extractKeywords(userMessage);
        int allowedRevealLevel = resolveAllowedRevealLevel(userMessage);

        List<ClinicalCaseFact> selectedFacts = new ArrayList<>();
        List<ClinicalCaseFact> newlyRevealedFacts = new ArrayList<>();

        for (ClinicalCaseFact fact : allFacts) {
            int factRevealLevel = fact.getNivelRevelacion() == null ? 1 : fact.getNivelRevelacion();
            boolean includeByDefault = factRevealLevel == 1;
            boolean includeAsPreviouslyRevealed = alreadyRevealedIds.contains(fact.getId());

            boolean includeAsNewReveal = false;
            if (!includeByDefault && !includeAsPreviouslyRevealed && factRevealLevel <= allowedRevealLevel) {
                includeAsNewReveal = matchesFactByKeyword(fact, messageKeywords);
            }

            if (includeByDefault || includeAsPreviouslyRevealed || includeAsNewReveal) {
                selectedFacts.add(fact);
            }

            if (includeAsNewReveal) {
                newlyRevealedFacts.add(fact);
                alreadyRevealedIds.add(fact.getId());
            }
        }

        persistNewlyRevealedFacts(sessionId, newlyRevealedFacts);
        return selectedFacts;
    }

    private void persistNewlyRevealedFacts(UUID sessionId, List<ClinicalCaseFact> newlyRevealedFacts) {
        if (newlyRevealedFacts == null || newlyRevealedFacts.isEmpty()) {
            return;
        }

        for (ClinicalCaseFact fact : newlyRevealedFacts) {
            if (sessionRevealedFactRepository.existsBySessionIdAndFactId(sessionId, fact.getId())) {
                continue;
            }

            try {
                sessionRevealedFactRepository.save(new SessionRevealedFact(
                        UUID.randomUUID(),
                        sessionId,
                        fact.getId(),
                        LocalDateTime.now()
                ));
            } catch (DataIntegrityViolationException ignored) {
                // Protección defensiva ante concurrencia o duplicados.
            }
        }
    }

    private int resolveAllowedRevealLevel(String userMessage) {
        String normalized = normalize(userMessage);
        if (normalized.isEmpty()) {
            return llmProperties.getRevealStrategy() == RevealStrategy.DIRECT ? 2 : 1;
        }

        int allowedLevel = userMessage != null && userMessage.contains("?") ? 2 : 1;
        if (containsAnyToken(normalized, LEVEL_2_HINTS)) {
            allowedLevel = Math.max(allowedLevel, 2);
        }

        if (containsAnyToken(normalized, LEVEL_3_HINTS)) {
            allowedLevel = 3;
        }

        RevealStrategy revealStrategy = llmProperties.getRevealStrategy();
        if (revealStrategy == RevealStrategy.DIRECT) {
            allowedLevel = Math.min(3, allowedLevel + 1);
        } else if (revealStrategy == RevealStrategy.RESTRICTIVE) {
            allowedLevel = Math.max(1, allowedLevel - 1);
        }

        return allowedLevel;
    }

    private boolean containsAnyToken(String normalizedMessage, List<String> hints) {
        return hints.stream().anyMatch(normalizedMessage::contains);
    }

    private boolean matchesFactByKeyword(ClinicalCaseFact fact, Set<String> messageKeywords) {
        if (messageKeywords == null || messageKeywords.isEmpty()) {
            return false;
        }

        String normalizedFactText = normalize((fact.getNombre() == null ? "" : fact.getNombre()) + " " +
                (fact.getContenidoPaciente() == null ? "" : fact.getContenidoPaciente()));

        if (normalizedFactText.isEmpty()) {
            return false;
        }

        for (String keyword : messageKeywords) {
            if (normalizedFactText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> extractKeywords(String userMessage) {
        String normalized = normalize(userMessage);
        if (normalized.isEmpty()) {
            return Set.of();
        }

        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        String lowerCased = text.toLowerCase(Locale.ROOT)
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u')
                .replace('ü', 'u');

        return NON_ALPHANUMERIC.matcher(lowerCased).replaceAll(" ").trim();
    }

    private String resolveNoInfoResponse(String contextNoInfoResponse) {
        if (llmProperties.getNoInfoResponse() != null && !llmProperties.getNoInfoResponse().trim().isEmpty()) {
            return llmProperties.getNoInfoResponse().trim();
        }
        return contextNoInfoResponse;
    }
}
