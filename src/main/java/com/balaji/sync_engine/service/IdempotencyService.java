package com.balaji.sync_engine.service;

import com.balaji.sync_engine.exception.IdempotencyKeyReuseException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    public static final Duration TTL = Duration.ofHours(24);

    private record StoredEntry(String requestFingerprint, Object response, Instant expiresAt) {}

    private final ConcurrentHashMap<String, StoredEntry> store = new ConcurrentHashMap<>();
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public IdempotencyService() {
        this(Clock.systemUTC());
    }

    // Secondary constructor for unit testing with mutable clock
    public IdempotencyService(Clock clock) {
        this.clock = clock;
    }
 

    @SuppressWarnings("unchecked")
    public <T> T execute(String scope, String idempotencyKey, String requestPayload, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        Instant now = clock.instant();
        store.values().removeIf(entry -> entry.expiresAt().isBefore(now));

        String compositeKey = scope + ":" + idempotencyKey;
        String fingerprint = sha256(requestPayload == null ? "" : requestPayload);

        StoredEntry existing = store.get(compositeKey);
        if (existing != null) {
            if (!Objects.equals(existing.requestFingerprint(), fingerprint)) {
                throw new IdempotencyKeyReuseException(idempotencyKey);
            }
            return (T) existing.response();
        }

        T result = action.get();
        store.put(compositeKey, new StoredEntry(fingerprint, result, now.plus(TTL)));
        return result;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}