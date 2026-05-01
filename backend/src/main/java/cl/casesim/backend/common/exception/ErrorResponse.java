package cl.casesim.backend.common.exception;

public record ErrorResponse(
        int status,
        String error,
        String message,
        java.util.List<ErrorDetail> details
) {

    public record ErrorDetail(
            String field,
            String message
    ) {
    }
}
