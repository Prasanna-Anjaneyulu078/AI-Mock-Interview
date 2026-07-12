package com.mockinterview.exception;

import com.mockinterview.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Validation failed", errors));
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(org.springframework.web.multipart.MultipartException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("Upload exceeds the maximum allowed size (10MB). Please reduce the file size and try again."));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("Database constraint violation: " + ex.getMostSpecificCause().getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maps the backend errorCode to a short, structured error type consumed by the
     * frontend notification UI (e.g. RATE_LIMIT_EXCEEDED -> RATE_LIMIT).
     */
    private static String toErrorType(String errorCode) {
        if (errorCode == null) return "UNKNOWN";
        return switch (errorCode) {
            case "RATE_LIMIT_EXCEEDED" -> "RATE_LIMIT";
            case "INSUFFICIENT_CREDITS" -> "CREDITS_EXHAUSTED";
            case "ACCESS_DENIED" -> "ACCESS_DENIED";
            case "AI_PROVIDER_LIMIT" -> "QUOTA_EXCEEDED";
            case "INVALID_API_KEY" -> "AUTH_ERROR";
            case "SUBSCRIPTION_INACTIVE" -> "SUBSCRIPTION";
            case "JUDGE0_FORBIDDEN" -> "FORBIDDEN";
            case "JUDGE0_UNAVAILABLE" -> "UNAVAILABLE";
            case "ALL_PROVIDERS_FAILED" -> "ALL_PROVIDERS_FAILED";
            case "NO_PROVIDERS_CONFIGURED" -> "NO_PROVIDERS_CONFIGURED";
            case "INTERNAL_ERROR" -> "INTERNAL_ERROR";
            default -> errorCode;
        };
    }

    @ExceptionHandler(AIProviderException.class)
    public ResponseEntity<Map<String, Object>> handleAIProviderException(AIProviderException ex) {
        log.warn("AI Provider Error: {}", ex.getErrorMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("provider", ex.getProviderName());
        body.put("status", ex.getHttpStatus());
        body.put("errorCode", ex.getErrorCode());
        body.put("errorType", toErrorType(ex.getErrorCode()));
        body.put("message", ex.getErrorMessage());
        body.put("fallbackUsed", ex.isFallbackUsed());
        body.put("fallbackActivated", ex.isFallbackUsed());
        if (ex.getRetryAfter() != null) {
            body.put("retryAfter", ex.getRetryAfter());
        }
        if (ex.getFallbackData() != null) {
            body.put("fallbackData", ex.getFallbackData());
        }
        if (ex.getSelectedInterviewMode() != null) {
            body.put("selectedInterviewMode", ex.getSelectedInterviewMode());
        }
        if (ex.getQuestionSource() != null) {
            body.put("questionSource", ex.getQuestionSource());
        }
        if (ex.getSubErrors() != null) {
            // Include sub errors for the "AI Services Currently Unavailable" aggregated error
            body.put("subErrors", ex.getSubErrors());
        }
        return ResponseEntity.status(ex.getHttpStatus() > 0 ? ex.getHttpStatus() : HttpStatus.TOO_MANY_REQUESTS.value())
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex, org.springframework.web.context.request.WebRequest request) {
        log.error("Unhandled exception resulting in 500: ", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", java.time.LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        
        // NullPointerException has a null message, provide a fallback.
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "An unexpected internal server error occurred.";
        }
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
