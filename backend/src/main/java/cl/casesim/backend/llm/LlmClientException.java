package cl.casesim.backend.llm;

public class LlmClientException extends RuntimeException {

    private final LlmProviderError providerError;

    public LlmClientException(String message) {
        super(message);
        this.providerError = new LlmProviderError(LlmErrorCategory.UNKNOWN, null, message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
        this.providerError = new LlmProviderError(LlmErrorCategory.UNKNOWN, null, message);
    }

    public LlmClientException(String message, Throwable cause, LlmProviderError providerError) {
        super(message, cause);
        this.providerError = providerError;
    }

    public LlmProviderError providerError() {
        return providerError;
    }
}
