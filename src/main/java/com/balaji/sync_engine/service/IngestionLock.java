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

    public IngestionLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Waits until no other transaction is appending to the event log, then holds the lock until the
     * CALLER's transaction ends. Call it as the FIRST statement of every transaction that writes a
     * ChangeEvent. MANDATORY: fails loudly without a transaction, since a lock released immediately
     * would protect nothing.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        jdbcTemplate.query("select pg_advisory_xact_lock(?)", ps -> ps.setLong(1, LOCK_KEY), rs -> { });
    }
}