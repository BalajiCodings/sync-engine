package com.balaji.sync_engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.exception.IdempotencyKeyReuseException;
import com.balaji.sync_engine.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdempotencyServiceTest {

    private MutableClock clock;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-13T00:00:00Z"));
        service = new IdempotencyService(clock);
    }

    @Test
    void replayWithSameKeyAndPayloadReturnsCachedResultWithoutRunningActionAgain() {
        AtomicInteger calls = new AtomicInteger();

        String first = service.execute("user-1", "key-1", "{\"a\":1}", () -> "result-" + calls.incrementAndGet());
        String second = service.execute("user-1", "key-1", "{\"a\":1}", () -> "result-" + calls.incrementAndGet());

        assertEquals("result-1", first);
        assertEquals("result-1", second);
        assertEquals(1, calls.get());
    }

    @Test
    void reusingKeyWithDifferentPayloadThrowsIdempotencyKeyReuseException() {
        service.execute("user-1", "key-1", "{\"a\":1}", () -> "ok");

        assertThrows(
                IdempotencyKeyReuseException.class,
                () -> service.execute("user-1", "key-1", "{\"a\":2}", () -> "changed"));
    }

    @Test
    void differentUsersCanUseTheSameKeyIndependently() {
        String a = service.execute("user-1", "key-1", "{\"a\":1}", () -> "from-user-1");
        String b = service.execute("user-2", "key-1", "{\"a\":2}", () -> "from-user-2");

        assertEquals("from-user-1", a);
        assertEquals("from-user-2", b);
    }

    @Test
    void expiredEntryAllowsKeyToBeUsedAgain() {
        AtomicInteger calls = new AtomicInteger();
        service.execute("user-1", "key-1", "{\"a\":1}", calls::incrementAndGet);

        clock.advance(Duration.ofHours(25));

        service.execute("user-1", "key-1", "{\"a\":2}", calls::incrementAndGet);
        assertEquals(2, calls.get());
    }

    @Test
    void nullOrBlankKeyAlwaysExecutesAction() {
        AtomicInteger calls = new AtomicInteger();
        service.execute("user-1", null, "body", calls::incrementAndGet);
        service.execute("user-1", "  ", "body", calls::incrementAndGet);
        assertEquals(2, calls.get());
    }
}