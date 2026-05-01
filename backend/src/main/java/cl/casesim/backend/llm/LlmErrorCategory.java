package cl.casesim.backend.llm;

public enum LlmErrorCategory {
    AUTH_ERROR,
    QUOTA_EXCEEDED,
    RATE_LIMIT,
    TIMEOUT,
    INVALID_REQUEST,
    INVALID_RESPONSE,
    MODEL_NOT_FOUND,
    PROVIDER_UNAVAILABLE,
    UNKNOWN
}
