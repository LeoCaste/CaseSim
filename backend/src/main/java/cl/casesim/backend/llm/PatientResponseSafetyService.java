package cl.casesim.backend.llm;

public class PatientResponseSafetyService {

    private final ResponseSafetyFilter responseSafetyFilter;

    public PatientResponseSafetyService(ResponseSafetyFilter responseSafetyFilter) {
        this.responseSafetyFilter = responseSafetyFilter;
    }

    public String applyLlmResponse(String response, boolean enabledSafetyFilter, String noInformationPhrase) {
        return responseSafetyFilter.applyOrFallback(response, enabledSafetyFilter, noInformationPhrase);
    }

    public String applyContextualFallback(String fallbackResponse, boolean enabledSafetyFilter) {
        return responseSafetyFilter.applyOrFallback(fallbackResponse, enabledSafetyFilter, fallbackResponse);
    }

    public String applyTechnicalFallback(String fallbackResponse, boolean enabledSafetyFilter) {
        return responseSafetyFilter.applyOrFallback(fallbackResponse, enabledSafetyFilter, fallbackResponse);
    }

    public String applyLocalPatientFallback(String localFallback, boolean enabledSafetyFilter, String noInformationPhrase) {
        return responseSafetyFilter.applyOrFallback(localFallback, enabledSafetyFilter, noInformationPhrase);
    }

    public String applyRepetitionAlternative(String alternative, boolean enabledSafetyFilter, String noInformationPhrase) {
        return responseSafetyFilter.applyOrFallback(alternative, enabledSafetyFilter, noInformationPhrase);
    }
}
