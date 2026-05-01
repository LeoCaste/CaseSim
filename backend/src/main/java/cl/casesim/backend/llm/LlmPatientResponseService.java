package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonalityRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.ChatMessageRepository;
import cl.casesim.backend.sessions.PatientResponseService;
import cl.casesim.backend.sessions.SessionRevealedFact;
import cl.casesim.backend.sessions.SessionRevealedFactRepository;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Collectors;

public class LlmPatientResponseService implements PatientResponseService {

    private static final Logger log = LoggerFactory.getLogger(LlmPatientResponseService.class);

    private final LlmProperties llmProperties;
    private final LlmClient llmClient;
    private final PromptBuilderService promptBuilderService;
    private final ResponseSafetyFilter responseSafetyFilter;
    private final ChatMessageRepository chatMessageRepository;
    private final LlmUsageService llmUsageService;
    private final SimulationActivityRepository simulationActivityRepository;
    private final SimulationSessionRepository simulationSessionRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;
    private final ClinicalCaseFactRepository clinicalCaseFactRepository;
    private final ClinicalCasePersonalityRepository clinicalCasePersonalityRepository;
    private final SessionRevealedFactRepository sessionRevealedFactRepository;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N} ]");
    private static final String DEFAULT_SAFE_NO_INFO_RESPONSE = "No tengo información asociada a eso.";
    private static final String TECHNICAL_FALLBACK_RESPONSE = "Perdón, me cuesta responder en este momento. ¿Podrías repetir tu pregunta?";
    private static final String CONTEXT_FALLBACK_RESPONSE = "No pude cargar el contexto clínico de esta sesión. Intenta nuevamente en unos segundos o reinicia la sesión.";
    private static final List<String> LEVEL_2_HINTS = List.of("que", "como", "donde", "cuando", "cuanto", "tiene", "siente", "dolor", "fiebre", "tos", "sintoma", "vomito", "nausea");
    private static final List<String> LEVEL_3_HINTS = List.of("desde", "antecedente", "alergia", "medicamento", "cirugia", "hospital", "laboratorio", "examen", "resultado", "familiar", "cronico", "tratamiento");
    private static final List<String> TEMPORAL_HINTS = List.of("desde cuando", "hace cuanto", "inicio", "empezo", "comenzo", "cuanto tiempo", "desde");

    public LlmPatientResponseService(
            LlmProperties llmProperties,
            LlmClient llmClient,
            PromptBuilderService promptBuilderService,
            ResponseSafetyFilter responseSafetyFilter,
            ChatMessageRepository chatMessageRepository,
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
        String requestId = UUID.randomUUID().toString();
        int estimatedPromptTokens = llmUsageService.estimateTokens(userMessage);
        String resolvedModel = llmProperties.getModel();
        String resolvedProvider = llmProperties.getProvider();
        NoInfoResolution noInfoResolution = resolveNoInfoResponse(resolveCaseNoInfoResponse(session));

        if (!llmProperties.isEnabled() || !llmProperties.hasApiKey()) {
            return registerAndReturnTechnicalFallback(
                    session,
                    startedAt,
                    estimatedPromptTokens,
                    resolvedProvider,
                    resolvedModel,
                    noInfoResolution,
                    "LLM_DISABLED_OR_MISSING_API_KEY",
                    new RuntimeException("LLM deshabilitado o API key no configurada")
            );
        }

        try {
            List<ChatMessage> history = loadRecentHistory(session.getId());
            PromptBuilderService.ClinicalPromptContext context = buildPromptContext(session, userMessage);
            noInfoResolution = resolveNoInfoResponse(context.noInformationReply());
            int symptomsCount = countSymptomFacts(context.facts());
            boolean adminConfigPresent = hasText(llmProperties.getSystemPrompt()) || hasText(llmProperties.getPatientBehaviorRules());
            List<String> promptSectionsIncluded = buildPromptSectionsIncluded(context, adminConfigPresent);
            log.info(
                    "LLM clinical context requestId={} sessionId={} activityId={} caseId={} factsCount={} symptomsCount={} personalityPresent={} adminConfigPresent={} provider={} model={} promptSectionsIncluded={} revealStrategy={}",
                    requestId,
                    session.getId(),
                    session.getActividadId(),
                    context.clinicalCaseId(),
                    context.facts() == null ? 0 : context.facts().size(),
                    symptomsCount,
                    context.personalityTraits() != null && !context.personalityTraits().isEmpty(),
                    adminConfigPresent,
                    resolvedProvider,
                    resolvedModel,
                    String.join(",", promptSectionsIncluded),
                    llmProperties.getRevealStrategy() == null ? RevealStrategy.PROGRESSIVE : llmProperties.getRevealStrategy()
            );

            List<LlmClient.ChatPromptMessage> promptMessages = promptBuilderService.buildMessages(
                    context,
                    history,
                    userMessage,
                    new PromptBuilderService.PatientBehaviorConfig(
                            llmProperties.getSystemPrompt(),
                            llmProperties.getPatientBehaviorRules(),
                            noInfoResolution.value(),
                            llmProperties.getRevealStrategy()
                    )
            );

            estimatedPromptTokens = promptMessages.stream()
                    .map(LlmClient.ChatPromptMessage::content)
                    .mapToInt(llmUsageService::estimateTokens)
                    .sum();

            log.info(
                    "LLM request prepared requestId={} provider={} model={} promptChars={} revealStrategy={} noInfoConfigured={}",
                    requestId,
                    resolvedProvider,
                    resolvedModel,
                    promptMessages.stream().map(LlmClient.ChatPromptMessage::content).mapToInt(String::length).sum(),
                    llmProperties.getRevealStrategy(),
                    hasText(noInfoResolution.value())
            );
            log.debug(
                    "LLM prompt preview sessionId={} preview={}",
                    session.getId(),
                    maskForLog(buildPromptPreview(promptMessages))
            );

            String llmResponse;
            try {
                llmResponse = llmClient.generateChatCompletion(
                        promptMessages,
                        llmProperties.getTemperature(),
                        llmProperties.getMaxTokens()
                );
            } catch (OpenAiLlmClient.LlmClientException ex) {
                llmResponse = tryGenerateFallbackPatientResponse(
                        context,
                        userMessage,
                        noInfoResolution,
                        resolvedProvider,
                        resolvedModel,
                        ex
                );
                if (!hasText(llmResponse)) {
                    String contextualFallback = buildContextualPatientFallback(context, userMessage, noInfoResolution);
                    if (hasText(contextualFallback)) {
                        return registerAndReturnLocalPatientFallback(
                                session,
                                startedAt,
                                estimatedPromptTokens,
                                resolvedProvider,
                                resolvedModel,
                                noInfoResolution,
                                "PROVIDER_CALL_ERROR",
                                ex,
                                contextualFallback
                        );
                    }
                    return registerAndReturnTechnicalFallback(
                            session,
                            startedAt,
                            estimatedPromptTokens,
                            resolvedProvider,
                            resolvedModel,
                            noInfoResolution,
                            "PROVIDER_CALL_ERROR",
                            ex
                    );
                }
            }

            String safeResponse = responseSafetyFilter.applyOrFallback(
                    llmResponse,
                    llmProperties.isEnabledSafetyFilter(),
                    noInfoResolution.value()
            );
            String finalResponse = avoidConsecutiveRepetition(safeResponse, history, userMessage, context, noInfoResolution);
            boolean fallbackUsed = noInfoResolution.value().equals(finalResponse);
            log.info("LLM response completed requestId={} provider={} model={} fallbackUsed={} noInfoSource={} noInfoResponse={} safetyFilterApplied={}",
                    requestId,
                    resolvedProvider,
                    resolvedModel,
                    fallbackUsed,
                    noInfoResolution.source(),
                    maskForLog(noInfoResolution.value()),
                    fallbackUsed);

            safeRegisterUsage(
                    session.getId(),
                    resolvedProvider,
                    resolvedModel,
                    estimatedPromptTokens,
                    llmUsageService.estimateTokens(finalResponse),
                    (int) (System.currentTimeMillis() - startedAt),
                    fallbackUsed,
                    null
            );

            return finalResponse;
        } catch (ContextResolutionException ex) {
            return registerAndReturnContextFallback(
                    session,
                    startedAt,
                    estimatedPromptTokens,
                    resolvedProvider,
                    resolvedModel,
                    noInfoResolution,
                    "CONTEXT_LOAD_ERROR",
                    ex
            );
        } catch (RuntimeException ex) {
            return registerAndReturnTechnicalFallback(
                    session,
                    startedAt,
                    estimatedPromptTokens,
                    resolvedProvider,
                    resolvedModel,
                    noInfoResolution,
                    "PROMPT_OR_CONTEXT_ERROR",
                    ex
            );
        }
    }

    private String registerAndReturnContextFallback(
            SimulationSession session,
            long startedAt,
            int estimatedPromptTokens,
            String resolvedProvider,
            String resolvedModel,
            NoInfoResolution noInfoResolution,
            String stage,
            RuntimeException ex
    ) {
        String fallback = responseSafetyFilter.applyOrFallback(
                CONTEXT_FALLBACK_RESPONSE,
                llmProperties.isEnabledSafetyFilter(),
                CONTEXT_FALLBACK_RESPONSE
        );
        String errorType = ex.getClass().getSimpleName();
        String sanitizedReason = sanitizeError(ex.getMessage());
        String reason = stage + "|" + errorType + "|" + sanitizedReason;
        log.warn(
                "LLM context failed sessionId={} activityId={} provider={} model={} stage={} errorType={} noInfoSource={} reason={}",
                session.getId(),
                session.getActividadId(),
                resolvedProvider,
                resolvedModel,
                stage,
                errorType,
                noInfoResolution.source(),
                sanitizedReason
        );
        safeRegisterUsage(
                session.getId(),
                resolvedProvider,
                resolvedModel,
                estimatedPromptTokens,
                llmUsageService.estimateTokens(fallback),
                (int) (System.currentTimeMillis() - startedAt),
                true,
                reason
        );
        return fallback;
    }

    private String registerAndReturnTechnicalFallback(
            SimulationSession session,
            long startedAt,
            int estimatedPromptTokens,
            String resolvedProvider,
            String resolvedModel,
            NoInfoResolution noInfoResolution,
            String stage,
            RuntimeException ex
    ) {
        String fallback = responseSafetyFilter.applyOrFallback(
                TECHNICAL_FALLBACK_RESPONSE,
                llmProperties.isEnabledSafetyFilter(),
                TECHNICAL_FALLBACK_RESPONSE
        );
        String errorType = ex.getClass().getSimpleName();
        String sanitizedReason = sanitizeError(ex.getMessage());
        String reason = stage + "|" + errorType + "|" + sanitizedReason;
        log.warn(
                "LLM request failed sessionId={} provider={} model={} fallbackUsed=true fallbackType=TECHNICAL stage={} errorType={} noInfoSource={} noInfoResponse={} safetyFilterApplied={} reason={}",
                session.getId(),
                resolvedProvider,
                resolvedModel,
                stage,
                errorType,
                noInfoResolution.source(),
                maskForLog(noInfoResolution.value()),
                llmProperties.isEnabledSafetyFilter(),
                sanitizedReason
        );
        safeRegisterUsage(
                session.getId(),
                resolvedProvider,
                resolvedModel,
                estimatedPromptTokens,
                llmUsageService.estimateTokens(fallback),
                (int) (System.currentTimeMillis() - startedAt),
                true,
                reason
        );
        return fallback;
    }

    private String registerAndReturnLocalPatientFallback(
            SimulationSession session,
            long startedAt,
            int estimatedPromptTokens,
            String resolvedProvider,
            String resolvedModel,
            NoInfoResolution noInfoResolution,
            String stage,
            RuntimeException ex,
            String localFallback
    ) {
        String safeResponse = responseSafetyFilter.applyOrFallback(
                localFallback,
                llmProperties.isEnabledSafetyFilter(),
                noInfoResolution.value()
        );
        String errorType = ex.getClass().getSimpleName();
        String sanitizedReason = sanitizeError(ex.getMessage());
        String reason = stage + "|" + errorType + "|" + sanitizedReason + "|LOCAL_PATIENT_FALLBACK";
        log.warn(
                "LLM provider failed; using local patient fallback sessionId={} provider={} model={} stage={} errorType={} reason={}",
                session.getId(),
                resolvedProvider,
                resolvedModel,
                stage,
                errorType,
                sanitizedReason
        );
        safeRegisterUsage(
                session.getId(),
                resolvedProvider,
                resolvedModel,
                estimatedPromptTokens,
                llmUsageService.estimateTokens(safeResponse),
                (int) (System.currentTimeMillis() - startedAt),
                true,
                reason
        );
        return safeResponse;
    }

    private String buildContextualPatientFallback(
            PromptBuilderService.ClinicalPromptContext context,
            String userMessage,
            NoInfoResolution noInfoResolution
    ) {
        if (context == null) {
            return null;
        }
        if (hasText(context.chiefComplaint())) {
            if (!hasText(userMessage) || userMessage.trim().length() <= 12) {
                String patientRef = hasText(context.patientName()) ? context.patientName().trim() : "la paciente";
                return "Hola, soy " + patientRef + ". " + context.chiefComplaint().trim();
            }
            return context.chiefComplaint().trim();
        }
        return noInfoResolution == null ? null : noInfoResolution.value();
    }

    private String avoidConsecutiveRepetition(
            String candidateResponse,
            List<ChatMessage> history,
            String userMessage,
            PromptBuilderService.ClinicalPromptContext context,
            NoInfoResolution noInfoResolution
    ) {
        if (!hasText(candidateResponse) || history == null || history.isEmpty()) {
            return candidateResponse;
        }

        String lastAssistant = lastAssistantMessage(history);
        if (!hasText(lastAssistant)) {
            return candidateResponse;
        }

        String normalizedCandidate = normalize(candidateResponse);
        String normalizedLastAssistant = normalize(lastAssistant);
        if (!hasText(normalizedCandidate) || !normalizedCandidate.equals(normalizedLastAssistant)) {
            return candidateResponse;
        }

        boolean hasFacts = context != null && context.facts() != null
                && context.facts().stream().anyMatch(this::hasText);
        boolean isGreetingTurn = isLikelyGreeting(userMessage);

        if (!hasFacts || isGreetingTurn) {
            return candidateResponse;
        }

        String alternative = buildAlternativeFromFacts(context, normalizedCandidate, noInfoResolution);
        if (hasText(alternative)) {
            return alternative;
        }

        if (isLikelyFollowUp(userMessage) && hasText(context.chiefComplaint())) {
            String normalizedChiefComplaint = normalize(context.chiefComplaint());
            if (normalizedCandidate.equals(normalizedChiefComplaint)) {
                String nonChiefAlternative = buildAlternativeAvoidingChiefComplaint(
                        context,
                        normalizedCandidate,
                        normalizedChiefComplaint,
                        noInfoResolution
                );
                if (hasText(nonChiefAlternative)) {
                    return nonChiefAlternative;
                }
            }
        }

        return candidateResponse;
    }

    private String lastAssistantMessage(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message != null && "ASSISTANT".equalsIgnoreCase(message.getRol()) && hasText(message.getContenido())) {
                return message.getContenido().trim();
            }
        }
        return null;
    }

    private boolean isLikelyGreeting(String userMessage) {
        String normalized = normalize(userMessage);
        if (!hasText(normalized)) {
            return true;
        }
        return normalized.equals("hola")
                || normalized.equals("buenas")
                || normalized.equals("buenos dias")
                || normalized.equals("buenas tardes")
                || normalized.equals("buenas noches");
    }

    private String buildAlternativeFromFacts(
            PromptBuilderService.ClinicalPromptContext context,
            String normalizedCandidate,
            NoInfoResolution noInfoResolution
    ) {
        if (context == null || context.facts() == null || context.facts().isEmpty()) {
            return null;
        }

        for (String fact : context.facts()) {
            if (!hasText(fact)) {
                continue;
            }
            String trimmedFact = fact.trim();
            if (normalize(trimmedFact).equals(normalizedCandidate)) {
                continue;
            }
            String factValue = extractFactValue(trimmedFact);
            if (!hasText(factValue)) {
                continue;
            }
            String safeAlternative = responseSafetyFilter.applyOrFallback(
                    factValue,
                    llmProperties.isEnabledSafetyFilter(),
                    noInfoResolution == null ? DEFAULT_SAFE_NO_INFO_RESPONSE : noInfoResolution.value()
            );
            if (hasText(safeAlternative) && !normalize(safeAlternative).equals(normalizedCandidate)) {
                return safeAlternative;
            }
        }

        return null;
    }

    private String buildAlternativeAvoidingChiefComplaint(
            PromptBuilderService.ClinicalPromptContext context,
            String normalizedCandidate,
            String normalizedChiefComplaint,
            NoInfoResolution noInfoResolution
    ) {
        if (context == null || context.facts() == null || context.facts().isEmpty()) {
            return null;
        }

        for (String fact : context.facts()) {
            if (!hasText(fact)) {
                continue;
            }
            String factValue = extractFactValue(fact.trim());
            if (!hasText(factValue)) {
                continue;
            }
            String normalizedFactValue = normalize(factValue);
            if (normalizedFactValue.equals(normalizedCandidate) || normalizedFactValue.equals(normalizedChiefComplaint)) {
                continue;
            }
            String safeAlternative = responseSafetyFilter.applyOrFallback(
                    factValue,
                    llmProperties.isEnabledSafetyFilter(),
                    noInfoResolution == null ? DEFAULT_SAFE_NO_INFO_RESPONSE : noInfoResolution.value()
            );
            if (hasText(safeAlternative) && !normalize(safeAlternative).equals(normalizedCandidate)) {
                return safeAlternative;
            }
        }

        return null;
    }

    private String extractFactValue(String factLine) {
        int separatorIndex = factLine.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == factLine.length() - 1) {
            return factLine;
        }
        return factLine.substring(separatorIndex + 1).trim();
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
        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId())
                .orElseThrow(() -> new ContextResolutionException("No existe actividad para la sesión " + session.getId()));

        ClinicalCase clinicalCase = clinicalCaseRepository.findById(activity.getCasoId())
                .orElseThrow(() -> new ContextResolutionException("No existe caso clínico para la actividad " + activity.getId()));

        List<ClinicalCaseFact> allFacts = clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(clinicalCase.getId());
        List<ClinicalCaseFact> selectedFacts = (allFacts == null || allFacts.isEmpty())
                ? List.of()
                : selectFactsForPrompt(session.getId(), userMessage, allFacts);

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
                clinicalCase.getId(),
                clinicalCase.getTitulo(),
                clinicalCase.getPacienteNombre(),
                clinicalCase.getPacienteEdad() == null ? null : String.valueOf(clinicalCase.getPacienteEdad()),
                clinicalCase.getPacienteSexo(),
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
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private String resolveCaseNoInfoResponse(SimulationSession session) {
        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId()).orElse(null);
        if (activity == null) {
            return null;
        }
        ClinicalCase clinicalCase = clinicalCaseRepository.findById(activity.getCasoId()).orElse(null);
        if (clinicalCase == null) {
            return null;
        }
        return clinicalCase.getFraseSinInformacion();
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
        boolean temporalIntent = isTemporalIntent(userMessage);
        RevealStrategy revealStrategy = llmProperties.getRevealStrategy() == null ? RevealStrategy.PROGRESSIVE : llmProperties.getRevealStrategy();
        int allowedRevealLevel = resolveAllowedRevealLevel(userMessage, revealStrategy);

        List<ClinicalCaseFact> selectedFacts = new ArrayList<>();
        List<ClinicalCaseFact> newlyRevealedFacts = new ArrayList<>();

        for (ClinicalCaseFact fact : allFacts) {
            int factRevealLevel = fact.getNivelRevelacion() == null ? 1 : fact.getNivelRevelacion();
            boolean includeByDefault = factRevealLevel == 1 || (revealStrategy == RevealStrategy.DIRECT && factRevealLevel <= 2);
            boolean includeAsPreviouslyRevealed = alreadyRevealedIds.contains(fact.getId());

            boolean includeAsNewReveal = false;
            if (!includeByDefault && !includeAsPreviouslyRevealed && factRevealLevel <= allowedRevealLevel) {
                includeAsNewReveal = matchesFactByKeyword(fact, messageKeywords);
                if (!includeAsNewReveal && temporalIntent && isTemporalFact(fact)) {
                    includeAsNewReveal = true;
                }
                if (!includeAsNewReveal && revealStrategy == RevealStrategy.DIRECT && factRevealLevel <= 2 && messageKeywords.isEmpty()) {
                    includeAsNewReveal = true;
                }
            }

            if (includeByDefault || includeAsPreviouslyRevealed || includeAsNewReveal) {
                selectedFacts.add(fact);
            }

            if (includeAsNewReveal) {
                newlyRevealedFacts.add(fact);
                alreadyRevealedIds.add(fact.getId());
            }
        }

        if (temporalIntent && !selectedFacts.isEmpty()) {
            selectedFacts = selectedFacts.stream()
                    .sorted(Comparator.comparing((ClinicalCaseFact fact) -> !isTemporalFact(fact)))
                    .collect(Collectors.toList());
        }

        if (selectedFacts.isEmpty()) {
            allFacts.stream()
                    .min(Comparator.comparing(fact -> fact.getNivelRevelacion() == null ? 1 : fact.getNivelRevelacion()))
                    .ifPresent(selectedFacts::add);
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

    private int resolveAllowedRevealLevel(String userMessage, RevealStrategy revealStrategy) {
        String normalized = normalize(userMessage);
        if (normalized.isEmpty()) {
            return revealStrategy == RevealStrategy.DIRECT ? 2 : 1;
        }

        int allowedLevel = userMessage != null && userMessage.contains("?") ? 2 : 1;
        if (containsAnyToken(normalized, LEVEL_2_HINTS)) {
            allowedLevel = Math.max(allowedLevel, 2);
        }

        if (containsAnyToken(normalized, LEVEL_3_HINTS)) {
            allowedLevel = 3;
        }

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

    private boolean isTemporalIntent(String userMessage) {
        String normalized = normalize(userMessage);
        if (!hasText(normalized)) {
            return false;
        }
        return TEMPORAL_HINTS.stream().anyMatch(normalized::contains);
    }

    private boolean isTemporalFact(ClinicalCaseFact fact) {
        if (fact == null) {
            return false;
        }
        String factText = normalize((fact.getNombre() == null ? "" : fact.getNombre()) + " "
                + (fact.getContenidoPaciente() == null ? "" : fact.getContenidoPaciente()) + " "
                + (fact.getTriggers() == null ? "" : fact.getTriggers()));
        if (!hasText(factText)) {
            return false;
        }
        return factText.contains("inicio")
                || factText.contains("desde")
                || factText.contains("hace")
                || factText.contains("duracion")
                || factText.contains("tiempo");
    }

    private boolean isLikelyFollowUp(String userMessage) {
        String normalized = normalize(userMessage);
        return hasText(normalized) && !isLikelyGreeting(normalized);
    }

    private boolean matchesFactByKeyword(ClinicalCaseFact fact, Set<String> messageKeywords) {
        if (messageKeywords == null || messageKeywords.isEmpty()) {
            return false;
        }

        String normalizedFactText = normalize((fact.getNombre() == null ? "" : fact.getNombre()) + " " +
                (fact.getContenidoPaciente() == null ? "" : fact.getContenidoPaciente()) + " " +
                (fact.getTriggers() == null ? "" : fact.getTriggers()));

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

    private NoInfoResolution resolveNoInfoResponse(String contextNoInfoResponse) {
        if (hasText(contextNoInfoResponse)) {
            return new NoInfoResolution(contextNoInfoResponse.trim(), "CASE");
        }
        if (hasText(llmProperties.getNoInfoResponse())) {
            return new NoInfoResolution(llmProperties.getNoInfoResponse().trim(), "ADMIN");
        }
        return new NoInfoResolution(DEFAULT_SAFE_NO_INFO_RESPONSE, "DEFAULT");
    }

    private int countSymptomFacts(List<String> facts) {
        if (facts == null || facts.isEmpty()) {
            return 0;
        }
        return (int) facts.stream()
                .filter(this::hasText)
                .map(this::normalize)
                .filter(text -> text.contains("sintoma") || text.contains("dolor") || text.contains("fiebre") || text.contains("tos") || text.contains("disnea"))
                .count();
    }

    private String tryGenerateFallbackPatientResponse(
            PromptBuilderService.ClinicalPromptContext originalContext,
            String userMessage,
            NoInfoResolution noInfoResolution,
            String resolvedProvider,
            String resolvedModel,
            OpenAiLlmClient.LlmClientException firstError
    ) {
        log.warn(
                "LLM primary call failed; attempting compact retry provider={} model={} clinicalCaseId={} reason={}",
                resolvedProvider,
                resolvedModel,
                originalContext.clinicalCaseId(),
                sanitizeError(firstError.getMessage())
        );

        List<String> compactFacts = new ArrayList<>();
        if (hasText(originalContext.chiefComplaint())) {
            compactFacts.add("motivo_consulta: " + originalContext.chiefComplaint().trim());
        }
        if (originalContext.facts() != null && !originalContext.facts().isEmpty()) {
            String firstFact = originalContext.facts().stream().filter(this::hasText).findFirst().orElse(null);
            if (hasText(firstFact)) {
                compactFacts.add(firstFact.trim());
            }
        }

        PromptBuilderService.ClinicalPromptContext compactContext = new PromptBuilderService.ClinicalPromptContext(
                originalContext.sessionId(),
                originalContext.clinicalCaseId(),
                originalContext.caseName(),
                originalContext.patientName(),
                originalContext.patientAge(),
                originalContext.patientSex(),
                originalContext.chiefComplaint(),
                originalContext.caseHistory(),
                originalContext.noInformationReply(),
                originalContext.personalityTraits(),
                compactFacts.isEmpty() ? originalContext.facts() : compactFacts
        );

        List<LlmClient.ChatPromptMessage> compactPrompt = promptBuilderService.buildMessages(
                compactContext,
                List.of(),
                userMessage,
                new PromptBuilderService.PatientBehaviorConfig(
                        llmProperties.getSystemPrompt(),
                        llmProperties.getPatientBehaviorRules(),
                        noInfoResolution.value(),
                        llmProperties.getRevealStrategy()
                )
        );

        try {
            String retryResponse = llmClient.generateChatCompletion(
                    compactPrompt,
                    llmProperties.getTemperature(),
                    llmProperties.getMaxTokens()
            );
            log.info("LLM compact retry succeeded provider={} model={}", resolvedProvider, resolvedModel);
            return retryResponse;
        } catch (OpenAiLlmClient.LlmClientException retryError) {
            log.warn(
                    "LLM compact retry failed provider={} model={} reason={}",
                    resolvedProvider,
                    resolvedModel,
                    sanitizeError(retryError.getMessage())
            );
            return null;
        }
    }

    private String buildPromptPreview(List<LlmClient.ChatPromptMessage> promptMessages) {
        if (promptMessages == null || promptMessages.isEmpty()) {
            return "<empty-prompt>";
        }
        return promptMessages.stream()
                .limit(4)
                .map(msg -> msg.role() + ":" + maskForLog(msg.content()))
                .reduce((a, b) -> a + " | " + b)
                .orElse("<empty-prompt>");
    }

    private String maskForLog(String value) {
        if (!hasText(value)) {
            return "<empty>";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120) + "...";
    }

    private String sanitizeError(String rawError) {
        if (rawError == null || rawError.isBlank()) {
            return "Error LLM no especificado.";
        }
        String sanitized = rawError.trim();
        if (llmProperties.getApiKey() != null && !llmProperties.getApiKey().isBlank()) {
            sanitized = sanitized.replace(llmProperties.getApiKey().trim(), "***");
        }
        if (sanitized.length() > 400) {
            sanitized = sanitized.substring(0, 400);
        }
        return sanitized;
    }

    private void safeRegisterUsage(
            UUID sessionId,
            String provider,
            String model,
            int promptTokens,
            int completionTokens,
            Integer latencyMs,
            boolean fallbackUsed,
            String error
    ) {
        try {
            llmUsageService.registerCall(
                    sessionId,
                    provider,
                    model,
                    promptTokens,
                    completionTokens,
                    latencyMs,
                    fallbackUsed,
                    error
            );
        } catch (RuntimeException registerError) {
            log.error(
                    "LLM usage metric persistence failed sessionId={} provider={} model={} fallbackUsed={} reason={}",
                    sessionId,
                    provider,
                    model,
                    fallbackUsed,
                    sanitizeError(registerError.getMessage())
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<String> buildPromptSectionsIncluded(PromptBuilderService.ClinicalPromptContext context, boolean adminConfigPresent) {
        List<String> sections = new ArrayList<>();
        sections.add("ADMIN_INSTITUTIONAL");
        if (adminConfigPresent) {
            sections.add("ADMIN_BEHAVIOR_RULES");
        }
        sections.add("PROFESSOR_CLINICAL_CONTEXT");
        if (context.personalityTraits() != null && !context.personalityTraits().isEmpty()) {
            sections.add("PROFESSOR_PERSONALITY");
        }
        sections.add("ROLE_AND_NO_DIAGNOSIS_POLICY");
        sections.add("NO_INFO_POLICY");
        return sections;
    }

    private record NoInfoResolution(String value, String source) {
    }

    private static class ContextResolutionException extends RuntimeException {
        private ContextResolutionException(String message) {
            super(message);
        }
    }
}
