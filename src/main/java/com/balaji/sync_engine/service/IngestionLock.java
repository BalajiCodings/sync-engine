package com.balaji.sync_engine.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IngestionLock {

    /** Arbitrary constant that names this lock inside Postgres. */
    private static final long LOCK_KEY = 7_261_001L;

    private final JdbcTemplate jdbcTemplate;
    private final SyncMetrics syncMetrics;

    public IngestionLock(JdbcTemplate jdbcTemplate, SyncMetrics syncMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncMetrics = syncMetrics;
    }

    /**
     * Waits until no other transaction is appending to the event log, then holds the lock until the
     * CALLER's transaction ends. Measures lock wait time via Micrometer Timer.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        syncMetrics.recordLockWait(() ->
            jdbcTemplate.query("select pg_advisory_xact_lock(?)", ps -> ps.setLong(1, LOCK_KEY), rs -> { })
        );
    }
}