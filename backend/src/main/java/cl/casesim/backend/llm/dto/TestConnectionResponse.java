package cl.casesim.backend.llm.dto;

public record TestConnectionResponse(
        boolean success,
        Integer httpStatus,
        String publicMessage,
        String message,
        Integer statusCode,
        String errorCode,
        String provider,
        String model,
        String endpointPath,
        String resolvedBaseHost,
        String traceId,
        String detail
) {

    public TestConnectionResponse(boolean success, String message) {
        this(success, success ? 200 : 500, message, message, success ? 200 : 500, null, null, null, null, null, null, null);
    }
}
