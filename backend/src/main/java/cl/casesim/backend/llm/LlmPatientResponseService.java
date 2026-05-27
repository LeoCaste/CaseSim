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
    private static final String QUOTA_FALLBACK_RESPONSE = "Estoy con alta demanda en este momento. Si te parece, continuamos con preguntas concretas de síntomas, tiempos o antecedentes mientras se restablece el servicio.";
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
                    new LlmUnavailableException("Servicio de simulación IA no disponible: configuración LLM incompleta."),
                    requestId,
                    null,
                    0,
                    estimatedPromptTokens
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

            List<LlmMessage> promptMessages = promptBuilderService.buildMessages(
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
                    .map(LlmMessage::content)
                    .mapToInt(llmUsageService::estimateTokens)
                    .sum();
            int promptChars = promptMessages.stream()
                    .map(LlmMessage::content)
                    .mapToInt(content -> content == null ? 0 : content.length())
                    .sum();

            log.info(
                    "LLM request prepared requestId={} provider={} model={} promptChars={} revealStrategy={} noInfoConfigured={}",
                    requestId,
                    resolvedProvider,
                    resolvedModel,
                    promptChars,
                    llmProperties.getRevealStrategy(),
                    hasText(noInfoResolution.value())
            );
            log.debug(
                    "LLM prompt preview sessionId={} preview={}",
                    session.getId(),
                    maskForLog(buildPromptPreview(promptMessages))
            );

            String llmResponse;
            LlmResponse providerResponse = null;
            String metricProvider = resolvedProvider;
            String metricModel = resolvedModel;
            Integer providerPromptTokens = null;
            Integer providerCompletionTokens = null;
            try {
                providerResponse = llmClient.generate(new LlmRequest(
                        promptMessages,
                        llmProperties.getModel(),
                        llmProperties.getTemperature(),
                        llmProperties.getMaxTokens()
                ));
                llmResponse = providerResponse == null ? "" : providerResponse.content();
                metricProvider = resolveMetricProvider(providerResponse, resolvedProvider);
                metricModel = resolveMetricModel(providerResponse, resolvedModel);
                providerPromptTokens = resolvePromptTokens(providerResponse);
                providerCompletionTokens = resolveCompletionTokens(providerResponse);
                log.info(
                        "LLM SUCCESS requestId={} sessionId={} caseId={} provider={} model={} origin={} promptChars={} promptTokensEstimate={} completionChars={} completionTokensEstimate={}",
                        requestId,
                        session.getId(),
                        context.clinicalCaseId(),
                        resolvedProvider,
                        resolvedModel,
                        "PROVIDER_PRIMARY",
                        promptChars,
                        estimatedPromptTokens,
                        llmResponse == null ? 0 : llmResponse.length(),
                        llmUsageService.estimateTokens(llmResponse)
                );
            } catch (LlmClientException ex) {
                llmResponse = tryGenerateFallbackPatientResponse(
                        context,
                        userMessage,
                        noInfoResolution,
                        resolvedProvider,
                        resolvedModel,
                        ex
                );
                if (!hasText(llmResponse)) {
                    String fallbackCause = classifyFallbackCause(ex);
                    String contextualFallback = buildContextualPatientFallback(context, userMessage, noInfoResolution);
                    if (hasText(contextualFallback)) {
                        return registerAndReturnLocalPatientFallback(
                                session,
                                startedAt,
                                estimatedPromptTokens,
                                resolvedProvider,
                                resolvedModel,
                                noInfoResolution,
                                "PROVIDER_CALL_ERROR|" + fallbackCause,
                                ex,
                                contextualFallback,
                                requestId,
                                context.clinicalCaseId(),
                                promptChars,
                                estimatedPromptTokens
                        );
                    }
                    return registerAndReturnTechnicalFallback(
                            session,
                            startedAt,
                            estimatedPromptTokens,
                            resolvedProvider,
                            resolvedModel,
                            noInfoResolution,
                            "PROVIDER_CALL_ERROR|" + fallbackCause,
                            ex,
                            requestId,
                            context.clinicalCaseId(),
                            promptChars,
                            estimatedPromptTokens
                    );
                }
                log.info(
                        "LLM SUCCESS requestId={} sessionId={} caseId={} provider={} model={} origin={} promptChars={} promptTokensEstimate={} completionChars={} completionTokensEstimate={}",
                        requestId,
                        session.getId(),
                        context.clinicalCaseId(),
                        resolvedProvider,
                        resolvedModel,
                        "PROVIDER_COMPACT_RETRY",
                        promptChars,
                        estimatedPromptTokens,
                        llmResponse.length(),
                        llmUsageService.estimateTokens(llmResponse)
                );
            }

            String safeResponse = responseSafetyFilter.applyOrFallback(
                    llmResponse,
                    llmProperties.isEnabledSafetyFilter(),
                    noInfoResolution.value()
            );
            String finalResponse = avoidConsecutiveRepetition(safeResponse, history, userMessage, context, noInfoResolution);
            boolean fallbackUsed = noInfoResolution.value().equals(finalResponse);
            String responseOrigin = fallbackUsed ? "FALLBACK_NO_INFO_OR_SAFETY" : "PROVIDER";
            log.info("LLM response completed requestId={} provider={} model={} fallbackUsed={} noInfoSource={} noInfoResponse={} safetyFilterApplied={}",
                    requestId,
                    resolvedProvider,
                    resolvedModel,
                    fallbackUsed,
                    noInfoResolution.source(),
                    maskForLog(noInfoResolution.value()),
                    fallbackUsed);
            if (fallbackUsed) {
                log.warn(
                        "LLM FALLBACK requestId={} sessionId={} caseId={} provider={} model={} origin={} promptChars={} promptTokensEstimate={} reason={}",
                        requestId,
                        session.getId(),
                        context.clinicalCaseId(),
                        resolvedProvider,
                        resolvedModel,
                        responseOrigin,
                        promptChars,
                        estimatedPromptTokens,
                        "SAFETY_OR_NO_INFO_RESPONSE"
                );
            }

            safeRegisterUsage(
                    session.getId(),
                    metricProvider,
                    metricModel,
                    providerPromptTokens == null ? estimatedPromptTokens : providerPromptTokens,
                    providerCompletionTokens == null ? llmUsageService.estimateTokens(finalResponse) : providerCompletionTokens,
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
                    ex,
                    requestId,
                    null,
                    0,
                    estimatedPromptTokens
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
                    ex,
                    requestId,
                    null,
                    0,
                    estimatedPromptTokens
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
            RuntimeException ex,
            String requestId,
            UUID caseId,
            int promptChars,
            int promptTokensEstimate
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
        log.warn(
                "LLM FALLBACK requestId={} sessionId={} caseId={} provider={} model={} origin={} stage={} cause={} exceptionClass={} exceptionMessage={} promptChars={} promptTokensEstimate={}",
                requestId,
                session.getId(),
                caseId,
                resolvedProvider,
                resolvedModel,
                "FALLBACK_CONTEXT",
                stage,
                classifyFallbackCause(ex),
                errorType,
                sanitizedReason,
                promptChars,
                promptTokensEstimate
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
            RuntimeException ex,
            String requestId,
            UUID caseId,
            int promptChars,
            int promptTokensEstimate
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
        log.warn(
                "LLM FALLBACK requestId={} sessionId={} caseId={} provider={} model={} origin={} stage={} cause={} exceptionClass={} exceptionMessage={} promptChars={} promptTokensEstimate={}",
                requestId,
                session.getId(),
                caseId,
                resolvedProvider,
                resolvedModel,
                "FALLBACK_TECHNICAL",
                stage,
                classifyFallbackCause(ex),
                errorType,
                sanitizedReason,
                promptChars,
                promptTokensEstimate
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
            String localFallback,
            String requestId,
            UUID caseId,
            int promptChars,
            int promptTokensEstimate
    ) {
        String safeResponse = responseSafetyFilter.applyOrFallback(
                localFallback,
                llmProperties.isEnabledSafetyFilter(),
                noInfoResolution.value()
        );
        String fallbackCause = classifyFallbackCause(ex);
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
        log.warn(
                "LLM FALLBACK requestId={} sessionId={} caseId={} provider={} model={} origin={} stage={} cause={} exceptionClass={} exceptionMessage={} promptChars={} promptTokensEstimate={}",
                requestId,
                session.getId(),
                caseId,
                resolvedProvider,
                resolvedModel,
                "FALLBACK_LOCAL_PATIENT",
                stage,
                fallbackCause,
                errorType,
                sanitizedReason,
                promptChars,
                promptTokensEstimate
        );
        if ("QUOTA_EXCEEDED".equals(fallbackCause)) {
            log.warn(
                    "LLM FALLBACK QUOTA_EXCEEDED requestId={} sessionId={} caseId={} provider={} model={} stage={} origin={}",
                    requestId,
                    session.getId(),
                    caseId,
                    resolvedProvider,
                    resolvedModel,
                    stage,
                    "FALLBACK_LOCAL_PATIENT"
            );
        }
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
        if (isLikelyQuotaMessage(userMessage)) {
            return QUOTA_FALLBACK_RESPONSE;
        }
        if (hasText(context.chiefComplaint())) {
            if (!hasText(userMessage) || userMessage.trim().length() <= 12 || isLikelyGreeting(userMessage)) {
                String patientRef = hasText(context.patientName()) ? context.patientName().trim() : "la paciente";
                return "Hola, soy " + patientRef + ". " + context.chiefComplaint().trim();
            }

            String normalizedMessage = normalize(userMessage);
            if (normalizedMessage.contains("desde") || normalizedMessage.contains("hace cuanto") || normalizedMessage.contains("cuanto tiempo")) {
                String temporalAlternative = firstTemporalFactValue(context);
                if (hasText(temporalAlternative)) {
                    return temporalAlternative;
                }
            }

            String alternativeFact = firstUsableFactValue(context, context.chiefComplaint());
            if (hasText(alternativeFact)) {
                return alternativeFact;
            }
            return context.chiefComplaint().trim();
        }
        return noInfoResolution == null ? null : noInfoResolution.value();
    }

    private String firstUsableFactValue(PromptBuilderService.ClinicalPromptContext context, String valueToAvoid) {
        if (context == null || context.facts() == null || context.facts().isEmpty()) {
            return null;
        }
        String normalizedAvoid = normalize(valueToAvoid);
        for (String fact : context.facts()) {
            if (!hasText(fact)) {
                continue;
            }
            String factValue = extractFactValue(fact.trim());
            if (!hasText(factValue)) {
                continue;
            }
            if (normalize(factValue).equals(normalizedAvoid)) {
                continue;
            }
            return factValue;
        }
        return null;
    }

    private String firstTemporalFactValue(PromptBuilderService.ClinicalPromptContext context) {
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
            if (normalize(factValue).contains("desde")
                    || normalize(factValue).contains("hace")
                    || normalize(factValue).contains("inicio")
                    || normalize(factValue).contains("comenzo")) {
                return factValue;
            }
        }
        return null;
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
            LlmClientException firstError
    ) {
        if ("QUOTA_EXCEEDED".equals(classifyFallbackCause(firstError))) {
            log.warn("LLM compact retry skipped due to quota provider={} model={} clinicalCaseId={}",
                    resolvedProvider,
                    resolvedModel,
                    originalContext.clinicalCaseId());
            return null;
        }
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

        List<LlmMessage> compactPrompt = promptBuilderService.buildMessages(
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
            LlmResponse retryProviderResponse = llmClient.generate(new LlmRequest(
                    compactPrompt,
                    llmProperties.getModel(),
                    llmProperties.getTemperature(),
                    llmProperties.getMaxTokens()
            ));
            String retryResponse = retryProviderResponse == null ? "" : retryProviderResponse.content();
            log.info("LLM compact retry succeeded provider={} model={}", resolvedProvider, resolvedModel);
            return retryResponse;
        } catch (LlmClientException retryError) {
            log.warn(
                    "LLM compact retry failed provider={} model={} reason={}",
                    resolvedProvider,
                    resolvedModel,
                    sanitizeError(retryError.getMessage())
            );
            return null;
        }
    }

    private String buildPromptPreview(List<LlmMessage> promptMessages) {
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
        sanitized = sanitized.replaceAll("(?i)bearer\\s+[a-z0-9_\\-\\.]+", "Bearer ***");
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key|x-goog-api-key)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
        if (sanitized.length() > 400) {
            sanitized = sanitized.substring(0, 400);
        }
        return sanitized;
    }

    private String classifyFallbackCause(RuntimeException ex) {
        if (ex == null) {
            return "UNKNOWN";
        }
        if (ex instanceof LlmClientException llmEx && llmEx.providerError() != null && llmEx.providerError().category() != null) {
            return llmEx.providerError().category().name();
        }
        String message = sanitizeError(ex.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return "TIMEOUT";
        }
        if (message.contains("context") && (message.contains("length") || message.contains("token") || message.contains("max"))) {
            return "TOKEN_LIMIT_OR_CONTEXT_LENGTH";
        }
        if (message.contains("json") || message.contains("parse") || message.contains("parseable")) {
            return "PARSE_JSON";
        }
        if (message.contains("payload") || message.contains("messages") || message.contains("input")) {
            return "PROMPT_MALFORMED_OR_PAYLOAD_INVALID";
        }
        if (message.contains("status=429")
                || message.contains("quota")
                || message.contains("insufficient")
                || message.contains("rate limit")) {
            return "QUOTA_EXCEEDED";
        }
        if (message.contains("model_invalid") || (message.contains("model") && (message.contains("invalid") || message.contains("not found") || message.contains("does not exist")))) {
            return "MODEL_OR_PARAMETER_INCOMPATIBILITY";
        }
        if (message.contains("serializ") || message.contains("deserialize")) {
            return "SERIALIZATION";
        }
        if (message.contains("null")) {
            return "NULL_VALUES";
        }
        if (message.contains("response vac") || message.contains("no parseable") || message.contains("http error") || message.contains("provider")) {
            return "PROVIDER_RESPONSE_INVALID";
        }
        return "UNKNOWN";
    }

    private boolean isLikelyQuotaMessage(String userMessage) {
        String normalized = normalize(userMessage);
        return normalized.contains("quota")
                || normalized.contains("sin cuota")
                || normalized.contains("sin saldo")
                || normalized.contains("no responde")
                || normalized.contains("no contestas")
                || normalized.contains("silencio");
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

    private String resolveMetricProvider(LlmResponse providerResponse, String fallbackProvider) {
        if (providerResponse == null || providerResponse.providerResult() == null) {
            return fallbackProvider;
        }
        return hasText(providerResponse.providerResult().provider())
                ? providerResponse.providerResult().provider().trim()
                : fallbackProvider;
    }

    private String resolveMetricModel(LlmResponse providerResponse, String fallbackModel) {
        if (providerResponse == null || providerResponse.providerResult() == null) {
            return fallbackModel;
        }
        return hasText(providerResponse.providerResult().model())
                ? providerResponse.providerResult().model().trim()
                : fallbackModel;
    }

    private Integer resolvePromptTokens(LlmResponse providerResponse) {
        return providerResponse == null || providerResponse.usage() == null
                ? null
                : providerResponse.usage().promptTokens();
    }

    private Integer resolveCompletionTokens(LlmResponse providerResponse) {
        return providerResponse == null || providerResponse.usage() == null
                ? null
                : providerResponse.usage().completionTokens();
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
