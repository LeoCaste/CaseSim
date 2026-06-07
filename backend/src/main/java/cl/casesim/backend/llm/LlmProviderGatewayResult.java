package cl.casesim.backend.llm;

public record LlmProviderGatewayResult(
        boolean primarySuccess,
        boolean compactRetrySuccess,
        boolean success,
        String response,
        LlmResponse providerResponse,
        String errorCause,
        String errorMessage,
        RuntimeException originalException
) {

    public static LlmProviderGatewayResult primarySuccess(String response, LlmResponse providerResponse) {
        return new LlmProviderGatewayResult(true, false, true, response, providerResponse, null, null, null);
    }

    public static LlmProviderGatewayResult compactRetrySuccess(String response, LlmResponse providerResponse) {
        return new LlmProviderGatewayResult(false, true, true, response, providerResponse, null, null, null);
    }

    public static LlmProviderGatewayResult allFailed(String errorCause, String errorMessage, RuntimeException originalException) {
        return new LlmProviderGatewayResult(false, false, false, null, null, errorCause, errorMessage, originalException);
    }
}
