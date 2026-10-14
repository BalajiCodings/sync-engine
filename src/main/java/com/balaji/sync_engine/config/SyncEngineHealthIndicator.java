package com.balaji.sync_engine.config;

import com.balaji.sync_engine.conflict.ConflictStatus;

import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.ConflictRecordRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SyncEngineHealthIndicator implements HealthIndicator {

    private static final long CONFLICT_WARNING_THRESHOLD = 1000L;

    private final ChangeEventRepository changeEventRepository;
    private final ConflictRecordRepository conflictRecordRepository;

    public SyncEngineHealthIndicator(ChangeEventRepository changeEventRepository,
                                     ConflictRecordRepository conflictRecordRepository) {
        this.changeEventRepository = changeEventRepository;
        this.conflictRecordRepository = conflictRecordRepository;
    }

    @Override
    public Health health() {
        try {
            long headSeq = changeEventRepository.findHeadSeq();
            long pendingConflicts = conflictRecordRepository.countByStatus(ConflictStatus.PENDING);

            Health.Builder builder = (pendingConflicts > CONFLICT_WARNING_THRESHOLD)
                    ? Health.status("WARN").withDetail("warning", "High number of unresolved conflicts")
                    : Health.up();

            return builder
                    .withDetail("headSeq", headSeq)
                    .withDetail("pendingConflicts", pendingConflicts)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("error", "Failed to query sync log state")
                    .build();
        }
    }
}