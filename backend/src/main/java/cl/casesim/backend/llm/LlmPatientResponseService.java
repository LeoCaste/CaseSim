package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.PatientResponseService;
import cl.casesim.backend.sessions.SimulationSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cl.casesim.backend.llm.FallbackCauseClassifier.classifyFallbackCause;
import static cl.casesim.backend.llm.TextNormalizationUtil.hasText;
import static cl.casesim.backend.llm.TextNormalizationUtil.maskForLog;

public class LlmPatientResponseService implements PatientResponseService {

    private static final Logger log = LoggerFactory.getLogger(LlmPatientResponseService.class);

    private final LlmProperties llmProperties;
    private final LlmClient llmClient;
    private final PromptBuilderService promptBuilderService;
    private final PatientResponseSafetyService patientResponseSafetyService;
    private final PatientFallbackResponseService patientFallbackResponseService;
    private final ConversationHistoryAssembler conversationHistoryAssembler;
    private final ClinicalCasePromptContextAssembler clinicalCasePromptContextAssembler;
    private final LlmInteractionMetricsService llmInteractionMetricsService;
    private final ClinicalCaseFactRepository clinicalCaseFactRepository;
    private final RevealableFactSelector revealableFactSelector;

    private static final String DEFAULT_SAFE_NO_INFO_RESPONSE = PatientFallbackResponseService.DEFAULT_SAFE_NO_INFO_RESPONSE;
    private static final String TECHNICAL_FALLBACK_RESPONSE = "Perdón, me cuesta responder en este momento. ¿Podrías repetir tu pregunta?";
    private static final String CONTEXT_FALLBACK_RESPONSE = "No pude cargar el contexto clínico de esta sesión. Intenta nuevamente en unos segundos o reinicia la sesión.";

    public LlmPatientResponseService(
            LlmProperties llmProperties,
            LlmClient llmClient,
            PromptBuilderService promptBuilderService,
            PatientResponseSafetyService patientResponseSafetyService,
            PatientFallbackResponseService patientFallbackResponseService,
            ConversationHistoryAssembler conversationHistoryAssembler,
            ClinicalCasePromptContextAssembler clinicalCasePromptContextAssembler,
            LlmInteractionMetricsService llmInteractionMetricsService,
            ClinicalCaseFactRepository clinicalCaseFactRepository,
            RevealableFactSelector revealableFactSelector
    ) {
        this.llmProperties = llmProperties;
        this.llmClient = llmClient;
        this.promptBuilderService = promptBuilderService;
        this.patientResponseSafetyService = patientResponseSafetyService;
        this.patientFallbackResponseService = patientFallbackResponseService;
        this.conversationHistoryAssembler = conversationHistoryAssembler;
        this.clinicalCasePromptContextAssembler = clinicalCasePromptContextAssembler;
        this.llmInteractionMetricsService = llmInteractionMetricsService;
        this.clinicalCaseFactRepository = clinicalCaseFactRepository;
        this.revealableFactSelector = revealableFactSelector;
    }

    @Override
    public String generateResponse(SimulationSession session, String userMessage) {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        int estimatedPromptTokens = llmInteractionMetricsService.estimateTokens(userMessage);
        String resolvedModel = llmProperties.getModel();
        String resolvedProvider = llmProperties.getProvider();
        NoInfoResolution noInfoResolution = resolveNoInfoResponse(clinicalCasePromptContextAssembler.resolveCaseNoInfoResponse(session));

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
            List<ChatMessage> history = conversationHistoryAssembler.loadRecentHistory(session.getId());
            PromptBuilderService.ClinicalPromptContext context = buildPromptContext(session, userMessage);
            noInfoResolution = resolveNoInfoResponse(context.noInformationReply());
            int symptomsCount = llmInteractionMetricsService.countSymptomFacts(context.facts());
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
                    .mapToInt(llmInteractionMetricsService::estimateTokens)
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
                metricProvider = llmInteractionMetricsService.resolveMetricProvider(providerResponse, resolvedProvider);
                metricModel = llmInteractionMetricsService.resolveMetricModel(providerResponse, resolvedModel);
                providerPromptTokens = llmInteractionMetricsService.resolvePromptTokens(providerResponse);
                providerCompletionTokens = llmInteractionMetricsService.resolveCompletionTokens(providerResponse);
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
                        llmInteractionMetricsService.estimateTokens(llmResponse)
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
                    String fallbackCause = classifyFallbackCause(ex, this::sanitizeError);
                    String contextualFallback = patientFallbackResponseService.buildContextualPatientFallback(context, userMessage, noInfoResolution.value());
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
                        llmInteractionMetricsService.estimateTokens(llmResponse)
                );
            }

            String safeResponse = patientResponseSafetyService.applyLlmResponse(
                    llmResponse,
                    llmProperties.isEnabledSafetyFilter(),
                    noInfoResolution.value()
            );
            String finalResponse = patientFallbackResponseService.avoidConsecutiveRepetition(
                    safeResponse,
                    history,
                    userMessage,
                    context,
                    noInfoResolution.value(),
                    llmProperties.isEnabledSafetyFilter()
            );
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

            llmInteractionMetricsService.safeRegisterUsage(
                    session.getId(),
                    metricProvider,
                    metricModel,
                    providerPromptTokens == null ? estimatedPromptTokens : providerPromptTokens,
                    providerCompletionTokens == null ? llmInteractionMetricsService.estimateTokens(finalResponse) : providerCompletionTokens,
                    (int) (System.currentTimeMillis() - startedAt),
                    fallbackUsed,
                    null
            );

            return finalResponse;
        } catch (ClinicalContextResolutionException ex) {
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
        String fallback = patientResponseSafetyService.applyContextualFallback(
                CONTEXT_FALLBACK_RESPONSE,
                llmProperties.isEnabledSafetyFilter()
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
                classifyFallbackCause(ex, this::sanitizeError),
                errorType,
                sanitizedReason,
                promptChars,
                promptTokensEstimate
        );
        llmInteractionMetricsService.safeRegisterUsage(
                session.getId(),
                resolvedProvider,
                resolvedModel,
                estimatedPromptTokens,
                llmInteractionMetricsService.estimateTokens(fallback),
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
        String fallback = patientResponseSafetyService.applyTechnicalFallback(
                TECHNICAL_FALLBACK_RESPONSE,
                llmProperties.isEnabledSafetyFilter()
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
                classifyFallbackCause(ex, this::sanitizeError),
                errorType,
                sanitizedReason,
                promptChars,
                promptTokensEstimate
        );
        llmInteractionMetricsService.safeRegisterUsage(
                session.getId(),
                resolvedProvider,
                resolvedModel,
                estimatedPromptTokens,
                llmInteractionMetricsService.estimateTokens(fallback),
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
        String safeResponse = patientResponseSafetyService.applyLocalPatientFallback(
                localFallback,
                llmProperties.isEnabledSafetyFilter(),
                noInfoResolution.value()
        );
        String fallbackCause = classifyFallbackCause(ex, this::sanitizeError);
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
        llmInteractionMetricsService.safeRegisterUsage(
                session.getId(),
                resolvedProvider,
                resolvedModel,
                estimatedPromptTokens,
                llmInteractionMetricsService.estimateTokens(safeResponse),
                (int) (System.currentTimeMillis() - startedAt),
                true,
                reason
        );
        return safeResponse;
    }

    private PromptBuilderService.ClinicalPromptContext buildPromptContext(SimulationSession session, String userMessage) {
        return clinicalCasePromptContextAssembler.assemble(session, clinicalCaseId -> {
            List<ClinicalCaseFact> allFacts = clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(clinicalCaseId);
            return (allFacts == null || allFacts.isEmpty())
                    ? List.of()
                    : revealableFactSelector.selectFactsForPrompt(session.getId(), userMessage, allFacts);
        });
    }

    private PromptBuilderService.ClinicalPromptContext emptyPromptContext(SimulationSession session) {
        return clinicalCasePromptContextAssembler.emptyPromptContext(session);
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

    private String tryGenerateFallbackPatientResponse(
            PromptBuilderService.ClinicalPromptContext originalContext,
            String userMessage,
            NoInfoResolution noInfoResolution,
            String resolvedProvider,
            String resolvedModel,
            LlmClientException firstError
    ) {
        if ("QUOTA_EXCEEDED".equals(classifyFallbackCause(firstError, this::sanitizeError))) {
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
            String firstFact = originalContext.facts().stream().filter(TextNormalizationUtil::hasText).findFirst().orElse(null);
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
                compactFacts.isEmpty() ? originalContext.facts() : compactFacts,
                originalContext.initialMessage(),
                originalContext.broaderContext(),
                originalContext.currentIllness(),
                originalContext.generalBackground(),
                originalContext.clinicalExamFindings(),
                originalContext.tone(),
                originalContext.detailLevel(),
                originalContext.behaviorGuidelines()
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

}
