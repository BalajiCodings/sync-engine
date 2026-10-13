package com.balaji.sync_engine.security;

import com.balaji.sync_engine.dto.ApiResult;
import com.balaji.sync_engine.dto.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Token Bucket rate limiter.
 * - Login: 10 requests per minute per IP (burst capacity 10).
 * - Push: 120 requests per minute per IP/Device (burst capacity 120).
 * - Other endpoints: 300 requests per minute.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final JsonMapper jsonMapper;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    private static class TokenBucket {
        private final double refillRatePerSecond;
        private final double capacity;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(double capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double seconds = (now - lastRefillTime) / 1000.0;
            if (seconds > 0) {
                tokens = Math.min(capacity, tokens + seconds * refillRatePerSecond);
                lastRefillTime = now;
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);

        String bucketKey;
        double capacity;
        double refillRate;

        if (path.contains("/auth/login")) {
            bucketKey = "login:" + clientIp;
            capacity = 10.0;
            refillRate = 10.0 / 60.0; // 10 per minute
        } else if (path.contains("/sync/push")) {
            bucketKey = "push:" + clientIp;
            capacity = 120.0;
            refillRate = 120.0 / 60.0; // 120 per minute
        } else {
            bucketKey = "general:" + clientIp;
            capacity = 300.0;
            refillRate = 300.0 / 60.0; // 300 per minute
        }

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(capacity, refillRate));

        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            jsonMapper.writeValue(response.getWriter(),
                    ApiResult.failure(ErrorCode.RATE_LIMIT_EXCEEDED,
                            "Too many requests. Rate limit exceeded for this endpoint. Please retry after 60 seconds."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}