package com.mockinterview.exception;

/**
 * Judge0 / RapidAPI returned HTTP 429 — the per-minute / monthly quota for the
 * subscription has been exceeded.
 */
public class Judge0QuotaExceededException extends AIProviderException {

    private final String rawBody;

    public Judge0QuotaExceededException(String rawBody) {
        super("Judge0", 429, "RATE_LIMIT_EXCEEDED", "Judge0 rate limit exceeded.", null);
        this.rawBody = rawBody;
    }

    /** The original RapidAPI response body, preserved for diagnostics. */
    public String getRawBody() {
        return rawBody;
    }
}
