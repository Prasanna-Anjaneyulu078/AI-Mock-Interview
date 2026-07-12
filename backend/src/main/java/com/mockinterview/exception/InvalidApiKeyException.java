package com.mockinterview.exception;

/**
 * Judge0 / RapidAPI returned HTTP 403 with a body indicating the {@code X-RapidAPI-Key}
 * is invalid, revoked, or wrong. Surfaced to the user as an actionable auth error rather
 * than a generic 500.
 */
public class InvalidApiKeyException extends AIProviderException {

    private final String rawBody;

    public InvalidApiKeyException(String rawBody) {
        super("Judge0", 403, "FORBIDDEN", "Judge0 API key invalid.", null);
        this.rawBody = rawBody;
    }

    /** The original RapidAPI response body, preserved for diagnostics. */
    public String getRawBody() {
        return rawBody;
    }
}
