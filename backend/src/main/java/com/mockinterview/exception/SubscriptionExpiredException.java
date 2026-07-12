package com.mockinterview.exception;

/**
 * Judge0 / RapidAPI returned HTTP 403 with a body indicating there is no active
 * subscription to the API (not subscribed, inactive, or expired plan).
 */
public class SubscriptionExpiredException extends AIProviderException {

    private final String rawBody;

    public SubscriptionExpiredException(String rawBody) {
        super("Judge0", 403, "SUBSCRIPTION_INACTIVE", "Judge0 subscription is inactive.", null);
        this.rawBody = rawBody;
    }

    /** The original RapidAPI response body, preserved for diagnostics. */
    public String getRawBody() {
        return rawBody;
    }
}
