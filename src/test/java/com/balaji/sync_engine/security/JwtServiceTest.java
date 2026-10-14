package com.balaji.sync_engine.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-must-be-at-least-256-bits-long-32bytes", 60000);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("test_worker");
        testUser.setRole(Role.FIELD_WORKER);
        testUser.setDeviceId("device-123");
    }

    @Test
    void generatesAndExtractsTokenClaims() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token);
        assertEquals("test_worker", jwtService.extractUsername(token));
        assertEquals("device-123", jwtService.extractDeviceId(token));
        assertTrue(jwtService.isTokenValid(token, "test_worker"));
        assertFalse(jwtService.isTokenValid(token, "wrong_user"));
    }

    @Test
    void detectsExpiredToken() throws InterruptedException {
        // Token expires in 1 millisecond
        JwtService shortLivedService = new JwtService("test-secret-key-must-be-at-least-256-bits-long-32bytes", 1);
        String token = shortLivedService.generateToken(testUser);

        Thread.sleep(10);
        assertFalse(shortLivedService.isTokenValid(token, "test_worker"));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(testUser);
        String tampered = token + "xyz";

        assertFalse(jwtService.isTokenValid(tampered, "test_worker"));
    }
}