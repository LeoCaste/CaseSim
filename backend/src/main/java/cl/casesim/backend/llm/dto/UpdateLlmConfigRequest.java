package cl.casesim.backend.llm.dto;

import cl.casesim.backend.llm.RevealStrategy;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateLlmConfigRequest(
        @NotBlank(message = "El proveedor es obligatorio.")
        @Size(max = 80, message = "El proveedor no puede superar 80 caracteres.")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "El proveedor contiene caracteres inválidos.")
        String provider,

        @NotBlank(message = "El modelo es obligatorio.")
        @Size(max = 100, message = "El modelo no puede superar 100 caracteres.")
        String model,

        @Size(max = 500, message = "La URL base no puede superar 500 caracteres.")
        String baseUrl,

        @NotNull(message = "El estado habilitado es obligatorio.")
        Boolean enabled,

        @Size(max = 500, message = "La API key no puede superar 500 caracteres.")
        String apiKey,

        @Size(max = 10000, message = "El systemPrompt no puede superar 10000 caracteres.")
        String systemPrompt,

        @Size(max = 10000, message = "Las reglas de comportamiento no pueden superar 10000 caracteres.")
        String patientBehaviorRules,

        @Size(max = 500, message = "La respuesta sin información no puede superar 500 caracteres.")
        String noInfoResponse,

        RevealStrategy revealStrategy,

        @Min(value = 1, message = "maxHistoryMessages debe ser mayor o igual a 1.")
        @Max(value = 30, message = "maxHistoryMessages debe ser menor o igual a 30.")
        Integer maxHistoryMessages,

        @DecimalMin(value = "0.0", message = "temperature debe ser mayor o igual a 0.0.")
        @DecimalMax(value = "2.0", message = "temperature debe ser menor o igual a 2.0.")
        Double temperature,

        @Min(value = 64, message = "maxTokens debe ser mayor o igual a 64.")
        @Max(value = 1024, message = "maxTokens debe ser menor o igual a 1024.")
        Integer maxTokens,

        Boolean enabledSafetyFilter,

        @Valid PatientBehaviorPayload patientBehavior
) {

    public String resolvedSystemPrompt() {
        return systemPrompt != null ? systemPrompt : (patientBehavior != null ? patientBehavior.basePrompt() : null);
    }

    public String resolvedPatientBehaviorRules() {
        return patientBehaviorRules != null
                ? patientBehaviorRules
                : (patientBehavior != null ? patientBehavior.additionalRules() : null);
    }

    public String resolvedNoInfoResponse() {
        return noInfoResponse != null
                ? noInfoResponse
                : (patientBehavior != null ? patientBehavior.noInformationReply() : null);
    }

    public RevealStrategy resolvedRevealStrategy() {
        return revealStrategy != null ? revealStrategy : (patientBehavior != null ? patientBehavior.revealStrategy() : null);
    }

    public Integer resolvedMaxHistoryMessages() {
        return maxHistoryMessages != null ? maxHistoryMessages : (patientBehavior != null ? patientBehavior.maxHistoryMessages() : null);
    }

    public Double resolvedTemperature() {
        return temperature != null ? temperature : (patientBehavior != null ? patientBehavior.temperature() : null);
    }

    public Integer resolvedMaxTokens() {
        return maxTokens != null ? maxTokens : (patientBehavior != null ? patientBehavior.maxTokens() : null);
    }

    public Boolean resolvedEnabledSafetyFilter() {
        return enabledSafetyFilter != null
                ? enabledSafetyFilter
                : (patientBehavior != null ? patientBehavior.safetyFilterEnabled() : null);
    }

    public record PatientBehaviorPayload(
            String basePrompt,
            String additionalRules,
            @JsonProperty("noInformationReply") String noInformationReply,
            RevealStrategy revealStrategy,
            @Min(value = 1, message = "maxHistoryMessages debe ser mayor o igual a 1.")
            @Max(value = 30, message = "maxHistoryMessages debe ser menor o igual a 30.")
            Integer maxHistoryMessages,
            @DecimalMin(value = "0.0", message = "temperature debe ser mayor o igual a 0.0.")
            @DecimalMax(value = "2.0", message = "temperature debe ser menor o igual a 2.0.")
            Double temperature,
            @Min(value = 64, message = "maxTokens debe ser mayor o igual a 64.")
            @Max(value = 1024, message = "maxTokens debe ser menor o igual a 1024.")
            Integer maxTokens,
            @JsonProperty("safetyFilterEnabled") Boolean safetyFilterEnabled
    ) {
    }
}
