package com.mockinterview.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 9: Simple in-memory rate limiter to protect authentication and API endpoints.
 * Limits are based on IP address and route prefix.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Value("${app.security.rate-limit.auth-requests-per-minute:5}")
    private int authRequestsPerMinute;

    @Value("${app.security.rate-limit.api-requests-per-minute:30}")
    private int apiRequestsPerMinute;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        
        // Define limit and key prefix based on path
        int limit;
        String prefix;
        if (path.startsWith("/api/auth")) {
            limit = authRequestsPerMinute;
            prefix = "auth:";
        } else if (path.startsWith("/api/")) {
            limit = apiRequestsPerMinute;
            prefix = "api:";
        } else {
            // Not a protected API path
            return true;
        }

        String key = prefix + clientIp;
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(limit));

        if (bucket.tryConsume()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.getWriter().write("Too many requests. Please try again later.");
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    /**
     * Very simple token bucket that refills exactly to the limit every minute.
     */
    private static class TokenBucket {
        private final int limit;
        private AtomicInteger tokens;
        private long windowStart;

        public TokenBucket(int limit) {
            this.limit = limit;
            this.tokens = new AtomicInteger(limit);
            this.windowStart = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                // Reset window
                tokens.set(limit);
                windowStart = now;
            }

            return tokens.decrementAndGet() >= 0;
        }
    }
}
