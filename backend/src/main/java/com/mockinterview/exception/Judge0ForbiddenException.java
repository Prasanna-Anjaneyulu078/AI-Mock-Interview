package com.mockinterview.exception;

/**
 * Judge0 / RapidAPI returned HTTP 403 that does not match the invalid-key or
 * subscription-inactive patterns (e.g. host mismatch, IP block, or plan restriction).
 */
public class Judge0ForbiddenException extends AIProviderException {

    private final String rawBody;

    public Judge0ForbiddenException(String rawBody) {
        super("Judge0", 403, "JUDGE0_FORBIDDEN", "Judge0 access forbidden.", null);
        this.rawBody = rawBody;
    }

    /** The original RapidAPI response body, preserved for diagnostics. */
    public String getRawBody() {
        return rawBody;
    }
}
