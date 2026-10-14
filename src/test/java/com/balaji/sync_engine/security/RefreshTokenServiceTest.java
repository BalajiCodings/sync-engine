package com.balaji.sync_engine.security;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository);
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("alice");
    }

    @Test
    void createsRefreshTokenSuccessfully() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken token = service.createRefreshToken(testUser);

        assertNotNull(token.getToken());
        assertEquals(testUser, token.getUser());
        assertFalse(token.isRevoked());
        assertTrue(token.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void rotatesValidRefreshToken() {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setToken("old-token");
        oldToken.setUser(testUser);
        oldToken.setRevoked(false);
        oldToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(repository.findByToken("old-token")).thenReturn(Optional.of(oldToken));
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken newToken = service.rotateRefreshToken("old-token");

        assertTrue(oldToken.isRevoked(), "Old token must be marked revoked");
        assertNotEquals("old-token", newToken.getToken(), "New token must have a different token string");
    }

    @Test
    void throwsExceptionWhenRotatingRevokedOrExpiredToken() {
        RefreshToken revoked = new RefreshToken();
        revoked.setToken("revoked-token");
        revoked.setRevoked(true);
        revoked.setExpiresAt(Instant.now().plusSeconds(3600));

        when(repository.findByToken("revoked-token")).thenReturn(Optional.of(revoked));

        assertThrows(NullPointerException.class, () -> service.rotateRefreshToken("revoked-token"));
    }
}