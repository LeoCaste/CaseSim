package cl.casesim.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.ResourceAccessException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MAX_VALIDATION_ERRORS_IN_RESPONSE = 3;

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), extractBadRequestDetails(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.ErrorDetail> details = mapFieldErrors(ex.getBindingResult().getFieldErrors());
        String message = buildFieldValidationMessage(ex.getBindingResult().getFieldErrors());
        return buildResponse(HttpStatus.BAD_REQUEST, message, details);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex) {
        List<ErrorResponse.ErrorDetail> details = mapFieldErrors(ex.getBindingResult().getFieldErrors());
        String message = buildFieldValidationMessage(ex.getBindingResult().getFieldErrors());
        return buildResponse(HttpStatus.BAD_REQUEST, message, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        List<ErrorResponse.ErrorDetail> details = extractJsonReadDetails(ex);
        String message = details.isEmpty()
                ? "El cuerpo de la solicitud es inválido."
                : "El cuerpo de la solicitud contiene campos inválidos.";
        return buildResponse(HttpStatus.BAD_REQUEST, message, details);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        DataIntegrityResult result = buildDataIntegrityResult(ex);
        return buildResponse(HttpStatus.BAD_REQUEST, result.message(), result.details());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch() {
        return buildResponse(HttpStatus.BAD_REQUEST, "Parámetro de solicitud inválido.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied() {
        return buildResponse(HttpStatus.FORBIDDEN, "Acceso denegado.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? status.getReasonPhrase()
                : ex.getReason();
        return buildResponse(status, message);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleResourceAccess(ResourceAccessException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Servicio temporalmente no disponible.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor.");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        return buildResponse(status, message, List.of());
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, List<ErrorResponse.ErrorDetail> details) {
        ErrorResponse body = new ErrorResponse(status.value(), resolveCode(status).name(), message, details);
        return ResponseEntity.status(status).body(body);
    }

    private ErrorCode resolveCode(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return ErrorCode.AUTH_UNAUTHORIZED;
        }
        if (status == HttpStatus.FORBIDDEN) {
            return ErrorCode.AUTH_FORBIDDEN;
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        return ErrorCode.UNKNOWN_ERROR;
    }

    private List<ErrorResponse.ErrorDetail> mapFieldErrors(List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return List.of();
        }
        return fieldErrors.stream()
                .map(error -> new ErrorResponse.ErrorDetail(
                        error.getField(),
                        error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage()
                ))
                .toList();
    }

    private String buildFieldValidationMessage(java.util.List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return "Solicitud inválida.";
        }

        java.util.List<String> details = fieldErrors.stream()
                .limit(MAX_VALIDATION_ERRORS_IN_RESPONSE)
                .map(error -> {
                    String field = error.getField();
                    String reason = error.getDefaultMessage() == null ? "valor inválido" : error.getDefaultMessage();
                    return field + ": " + reason;
                })
                .toList();

        String suffix = fieldErrors.size() > MAX_VALIDATION_ERRORS_IN_RESPONSE
                ? " (+" + (fieldErrors.size() - MAX_VALIDATION_ERRORS_IN_RESPONSE) + " error(es) más)"
                : "";

        return "Validación fallida en campos: " + String.join(" | ", details) + suffix;
    }

    private DataIntegrityResult buildDataIntegrityResult(DataIntegrityViolationException ex) {
        String rootCauseMessage = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        if (rootCauseMessage == null || rootCauseMessage.isBlank()) {
            return fallbackDataIntegrityResult();
        }

        String message = rootCauseMessage.toLowerCase();

        if (message.contains("nivel_revelacion") && (message.contains("check") || message.contains("between"))) {
            return new DataIntegrityResult(
                    "Los datos enviados no cumplen las restricciones requeridas.",
                    List.of(new ErrorResponse.ErrorDetail("facts[].revealLevel", "El nivel de revelación debe estar entre 1 y 4."))
            );
        }

        if (message.contains("contenido_paciente") && (message.contains("null value") || message.contains("not-null"))) {
            return new DataIntegrityResult(
                    "Los datos enviados no cumplen las restricciones requeridas.",
                    List.of(new ErrorResponse.ErrorDetail("facts[].content", "El contenido del hecho clínico no puede estar vacío."))
            );
        }

        if (message.contains("motivo_consulta") && (message.contains("null value") || message.contains("not-null"))) {
            return new DataIntegrityResult(
                    "Los datos enviados no cumplen las restricciones requeridas.",
                    List.of(new ErrorResponse.ErrorDetail("chiefComplaint", "El motivo de consulta es obligatorio."))
            );
        }

        if (message.contains("paciente_edad") && (message.contains("check") || message.contains("between") || message.contains("> 0") || message.contains("greater"))) {
            return new DataIntegrityResult(
                    "Los datos enviados no cumplen las restricciones requeridas.",
                    List.of(new ErrorResponse.ErrorDetail("patientAge", "La edad del paciente debe ser mayor a 0 cuando se informa."))
            );
        }

        if (message.contains("titulo") && (message.contains("null value") || message.contains("not-null"))) {
            return new DataIntegrityResult(
                    "Los datos enviados no cumplen las restricciones requeridas.",
                    List.of(new ErrorResponse.ErrorDetail("title", "El título es obligatorio."))
            );
        }

        if (message.contains("value too long") || message.contains("too long for type character varying")) {
            if (message.contains("titulo")) {
                return new DataIntegrityResult(
                        "Los datos enviados no cumplen las restricciones requeridas.",
                        List.of(new ErrorResponse.ErrorDetail("title", "El título supera el largo máximo permitido (200 caracteres)."))
                );
            }
            if (message.contains("paciente_nombre")) {
                return new DataIntegrityResult(
                        "Los datos enviados no cumplen las restricciones requeridas.",
                        List.of(new ErrorResponse.ErrorDetail("patientName", "El nombre del paciente supera el largo máximo permitido (120 caracteres)."))
                );
            }
            if (message.contains("paciente_sexo")) {
                return new DataIntegrityResult(
                        "Los datos enviados no cumplen las restricciones requeridas.",
                        List.of(new ErrorResponse.ErrorDetail("patientSex", "El sexo del paciente supera el largo máximo permitido (30 caracteres)."))
                );
            }
            if (message.contains("categoria")) {
                return new DataIntegrityResult(
                        "Los datos enviados no cumplen las restricciones requeridas.",
                        List.of(new ErrorResponse.ErrorDetail("facts[].category", "La categoría supera el largo máximo permitido (80 caracteres)."))
                );
            }
            if (message.contains("nombre")) {
                return new DataIntegrityResult(
                        "Los datos enviados no cumplen las restricciones requeridas.",
                        List.of(new ErrorResponse.ErrorDetail("facts[].key", "El nombre del hecho clínico supera el largo máximo permitido (150 caracteres)."))
                );
            }
        }

        return fallbackDataIntegrityResult();
    }

    private DataIntegrityResult fallbackDataIntegrityResult() {
        return new DataIntegrityResult(
                "Los datos enviados no cumplen las restricciones requeridas.",
                List.of(
                        new ErrorResponse.ErrorDetail("facts", "Revise revealLevel/content en cada hecho clínico."),
                        new ErrorResponse.ErrorDetail("chiefComplaint", "Es obligatorio para guardar el caso clínico.")
                )
        );
    }

    private List<ErrorResponse.ErrorDetail> extractBadRequestDetails(String message) {
        if (message == null || !message.contains(":")) {
            return List.of();
        }

        int idx = message.indexOf(':');
        String field = message.substring(0, idx).trim();
        String detailMessage = message.substring(idx + 1).trim();
        if (field.isEmpty() || detailMessage.isEmpty()) {
            return List.of();
        }
        return List.of(new ErrorResponse.ErrorDetail(field, capitalize(detailMessage)));
    }

    private List<ErrorResponse.ErrorDetail> extractJsonReadDetails(HttpMessageNotReadableException ex) {
        Throwable mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(ex);
        if (!(mostSpecificCause instanceof JacksonException jacksonException)) {
            return List.of();
        }

        String path = toPath(jacksonException.getPath());
        String detailMessage = "Valor o estructura inválida.";
        if (mostSpecificCause instanceof InvalidFormatException invalidFormatException) {
            detailMessage = "Tipo de dato inválido para el campo.";
            if (invalidFormatException.getTargetType() != null) {
                detailMessage = "Tipo de dato inválido, se esperaba " + invalidFormatException.getTargetType().getSimpleName() + ".";
            }
        }

        if (path.isBlank()) {
            return List.of(new ErrorResponse.ErrorDetail("body", detailMessage));
        }
        return List.of(new ErrorResponse.ErrorDetail(path, detailMessage));
    }

    private String toPath(List<JacksonException.Reference> pathReferences) {
        if (pathReferences == null || pathReferences.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JacksonException.Reference reference : pathReferences) {
            if (reference.getPropertyName() != null) {
                if (!builder.isEmpty()) {
                    builder.append('.');
                }
                builder.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                builder.append('[').append(reference.getIndex()).append(']');
            }
        }
        return builder.toString();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record DataIntegrityResult(String message, List<ErrorResponse.ErrorDetail> details) {
    }
}
