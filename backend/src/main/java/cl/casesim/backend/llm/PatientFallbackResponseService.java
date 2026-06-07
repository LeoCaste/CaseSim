package cl.casesim.backend.llm;

import cl.casesim.backend.sessions.ChatMessage;

import java.util.List;

import static cl.casesim.backend.llm.FallbackCauseClassifier.isLikelyFollowUp;
import static cl.casesim.backend.llm.FallbackCauseClassifier.isLikelyGreeting;
import static cl.casesim.backend.llm.FallbackCauseClassifier.isLikelyQuotaMessage;
import static cl.casesim.backend.llm.TextNormalizationUtil.extractFactValue;
import static cl.casesim.backend.llm.TextNormalizationUtil.hasText;
import static cl.casesim.backend.llm.TextNormalizationUtil.normalize;

public class PatientFallbackResponseService {

    static final String DEFAULT_SAFE_NO_INFO_RESPONSE = "No tengo información asociada a eso.";
    static final String QUOTA_FALLBACK_RESPONSE = "Estoy con alta demanda en este momento. Si te parece, continuamos con preguntas concretas de síntomas, tiempos o antecedentes mientras se restablece el servicio.";

    private final PatientResponseSafetyService patientResponseSafetyService;

    public PatientFallbackResponseService(PatientResponseSafetyService patientResponseSafetyService) {
        this.patientResponseSafetyService = patientResponseSafetyService;
    }

    public String buildContextualPatientFallback(
            PromptBuilderService.ClinicalPromptContext context,
            String userMessage,
            String noInfoResponse
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
        return noInfoResponse;
    }

    String firstUsableFactValue(PromptBuilderService.ClinicalPromptContext context, String valueToAvoid) {
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

    String firstTemporalFactValue(PromptBuilderService.ClinicalPromptContext context) {
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

    public String avoidConsecutiveRepetition(
            String candidateResponse,
            List<ChatMessage> history,
            String userMessage,
            PromptBuilderService.ClinicalPromptContext context,
            String noInfoResponse,
            boolean enabledSafetyFilter
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
                && context.facts().stream().anyMatch(TextNormalizationUtil::hasText);
        boolean isGreetingTurn = isLikelyGreeting(userMessage);

        if (!hasFacts || isGreetingTurn) {
            return candidateResponse;
        }

        String alternative = buildAlternativeFromFacts(context, normalizedCandidate, noInfoResponse, enabledSafetyFilter);
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
                        noInfoResponse,
                        enabledSafetyFilter
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

    String buildAlternativeFromFacts(
            PromptBuilderService.ClinicalPromptContext context,
            String normalizedCandidate,
            String noInfoResponse,
            boolean enabledSafetyFilter
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
            String safeAlternative = patientResponseSafetyService.applyRepetitionAlternative(
                    factValue,
                    enabledSafetyFilter,
                    hasText(noInfoResponse) ? noInfoResponse : DEFAULT_SAFE_NO_INFO_RESPONSE
            );
            if (hasText(safeAlternative) && !normalize(safeAlternative).equals(normalizedCandidate)) {
                return safeAlternative;
            }
        }

        return null;
    }

    String buildAlternativeAvoidingChiefComplaint(
            PromptBuilderService.ClinicalPromptContext context,
            String normalizedCandidate,
            String normalizedChiefComplaint,
            String noInfoResponse,
            boolean enabledSafetyFilter
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
            String safeAlternative = patientResponseSafetyService.applyRepetitionAlternative(
                    factValue,
                    enabledSafetyFilter,
                    hasText(noInfoResponse) ? noInfoResponse : DEFAULT_SAFE_NO_INFO_RESPONSE
            );
            if (hasText(safeAlternative) && !normalize(safeAlternative).equals(normalizedCandidate)) {
                return safeAlternative;
            }
        }

        return null;
    }
}
