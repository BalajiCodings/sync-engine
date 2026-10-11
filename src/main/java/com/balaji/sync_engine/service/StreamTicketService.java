package com.balaji.sync_engine.service;

import com.balaji.sync_engine.exception.InvalidStreamTicketException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StreamTicketService {

    public static final Duration TTL = Duration.ofSeconds(30);

    public record Ticket(String username, String deviceId, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(String username, String deviceId) {
        Instant now = Instant.now();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token, new Ticket(username, deviceId, now.plus(TTL)));
        return token;
    }

    /** Single use: removing the ticket IS the redemption, and it is atomic. */
    public Ticket redeem(String token) {
        Ticket ticket = tickets.remove(token);
        if (ticket == null || ticket.expiresAt().isBefore(Instant.now())) {
            throw new InvalidStreamTicketException();
        }
        return ticket;
    }
}