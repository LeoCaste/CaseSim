package cl.casesim.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static cl.casesim.backend.llm.FallbackCauseClassifier.classifyFallbackCause;
import static cl.casesim.backend.llm.TextNormalizationUtil.hasText;

public class LlmProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderGateway.class);

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final PromptBuilderService promptBuilderService;
    private final LlmErrorSanitizer llmErrorSanitizer;

    public LlmProviderGateway(
            LlmClient llmClient,
            LlmProperties llmProperties,
            PromptBuilderService promptBuilderService,
            LlmErrorSanitizer llmErrorSanitizer
    ) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.promptBuilderService = promptBuilderService;
        this.llmErrorSanitizer = llmErrorSanitizer;
    }

    public LlmProviderGatewayResult executeCall(
            List<LlmMessage> fullPromptMessages,
            PromptBuilderService.ClinicalPromptContext context,
            String userMessage,
            String noInfoResponseValue,
            String resolvedProvider,
            String resolvedModel
    ) {
        // 1. Primary call to the LLM provider
        try {
            LlmResponse providerResponse = llmClient.generate(new LlmRequest(
                    fullPromptMessages,
                    llmProperties.getModel(),
                    llmProperties.getTemperature(),
                    llmProperties.getMaxTokens()
            ));
            String response = providerResponse == null ? "" : providerResponse.content();
            log.info("LLM primary call succeeded provider={} model={}", resolvedProvider, resolvedModel);
            return LlmProviderGatewayResult.primarySuccess(response, providerResponse);
        } catch (LlmClientException primaryError) {
            // Check if compact retry should be skipped due to quota
            String primaryCause = classifyFallbackCause(primaryError, llmErrorSanitizer::sanitizeError);
            if ("QUOTA_EXCEEDED".equals(primaryCause)) {
                log.warn("LLM compact retry skipped due to quota provider={} model={} clinicalCaseId={}",
                        resolvedProvider,
                        resolvedModel,
                        context.clinicalCaseId());
                return LlmProviderGatewayResult.allFailed(
                        primaryCause,
                        llmErrorSanitizer.sanitizeError(primaryError.getMessage()),
                        primaryError
                );
            }

            log.warn(
                    "LLM primary call failed; attempting compact retry provider={} model={} clinicalCaseId={} reason={}",
                    resolvedProvider,
                    resolvedModel,
                    context.clinicalCaseId(),
                    llmErrorSanitizer.sanitizeError(primaryError.getMessage())
            );

            // 2. Compact retry with reduced context
            try {
                List<String> compactFacts = new ArrayList<>();
                if (hasText(context.chiefComplaint())) {
                    compactFacts.add("motivo_consulta: " + context.chiefComplaint().trim());
                }
                if (context.facts() != null && !context.facts().isEmpty()) {
                    String firstFact = context.facts().stream()
                            .filter(TextNormalizationUtil::hasText)
                            .findFirst()
                            .orElse(null);
                    if (hasText(firstFact)) {
                        compactFacts.add(firstFact.trim());
                    }
                }

                PromptBuilderService.ClinicalPromptContext compactContext = new PromptBuilderService.ClinicalPromptContext(
                        context.sessionId(),
                        context.clinicalCaseId(),
                        context.caseName(),
                        context.patientName(),
                        context.patientAge(),
                        context.patientSex(),
                        context.chiefComplaint(),
                        context.caseHistory(),
                        context.noInformationReply(),
                        context.personalityTraits(),
                        compactFacts.isEmpty() ? context.facts() : compactFacts,
                        context.initialMessage(),
                        context.broaderContext(),
                        context.currentIllness(),
                        context.generalBackground(),
                        context.clinicalExamFindings(),
                        context.tone(),
                        context.detailLevel(),
                        context.behaviorGuidelines()
                );

                List<LlmMessage> compactPrompt = promptBuilderService.buildMessages(
                        compactContext,
                        List.of(),
                        userMessage,
                        new PromptBuilderService.PatientBehaviorConfig(
                                llmProperties.getSystemPrompt(),
                                llmProperties.getPatientBehaviorRules(),
                                noInfoResponseValue,
                                llmProperties.getRevealStrategy()
                        )
                );

                LlmResponse retryProviderResponse = llmClient.generate(new LlmRequest(
                        compactPrompt,
                        llmProperties.getModel(),
                        llmProperties.getTemperature(),
                        llmProperties.getMaxTokens()
                ));
                String retryResponse = retryProviderResponse == null ? "" : retryProviderResponse.content();
                log.info("LLM compact retry succeeded provider={} model={}", resolvedProvider, resolvedModel);
                return LlmProviderGatewayResult.compactRetrySuccess(retryResponse, retryProviderResponse);
            } catch (LlmClientException retryError) {
                log.warn(
                        "LLM compact retry failed provider={} model={} reason={}",
                        resolvedProvider,
                        resolvedModel,
                        llmErrorSanitizer.sanitizeError(retryError.getMessage())
                );
                // Return the PRIMARY error for correct fallback classification (matches original behavior)
                return LlmProviderGatewayResult.allFailed(
                        primaryCause,
                        llmErrorSanitizer.sanitizeError(primaryError.getMessage()),
                        primaryError
                );
            }
        }
    }
}
