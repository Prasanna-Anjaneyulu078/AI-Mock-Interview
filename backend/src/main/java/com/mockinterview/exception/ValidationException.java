package com.mockinterview.exception;

/**
 * Thrown when an incoming request is syntactically valid (passes bean validation)
 * but violates a business rule that must be enforced server-side — for example,
 * starting a {@code RESUME_BASED} interview without an uploaded resume.
 *
 * <p>Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
