package com.balaji.sync_engine;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.dto.SyncPullRequest;
import com.balaji.sync_engine.dto.SyncPullResponse;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import com.balaji.sync_engine.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class SyncIngestionIT extends BaseIntegrationTest {

    @Autowired private SyncService syncService;
    @Autowired private ChangeEventRepository changeEventRepository;
    @Autowired private PatientRecordRepository patientRecordRepository;
    @Autowired private HybridLogicalClock clock;

    @BeforeEach
    void cleanUp() {
        patientRecordRepository.deleteAll();
        changeEventRepository.deleteAll();
    }

    private ChangeEvent buildEvent(UUID eventId, UUID entityId, ChangeType type, String payload, Long basedOnSeq) {
        ChangeEvent e = new ChangeEvent();
        e.setEventId(eventId);
        e.setEntityType("PatientRecord");
        e.setEntityId(entityId);
        e.setChangeType(type);
        e.setDeviceId("tablet-alpha");
        e.setHlcTimestamp(clock.tick().toString());
        e.setPayload(payload);
        e.setBasedOnSeq(basedOnSeq);
        return e;
    }

    @Test
    void perEventRequiresNewIsolation_oneBadEventDoesNotRollbackGoodEvents() {
        UUID patient1 = UUID.randomUUID();
        UUID patient2 = UUID.randomUUID();
        UUID patient3 = UUID.randomUUID();

        ChangeEvent good1 = buildEvent(UUID.randomUUID(), patient1, ChangeType.CREATE,
                "{\"patientName\":\"Alice\",\"weight\":65.0}", null);

        // Bad event: basedOnSeq is set to 999999L (ahead of the server log), which triggers validation failure
        ChangeEvent bad = buildEvent(UUID.randomUUID(), patient2, ChangeType.CREATE,
                "{\"patientName\":\"Bob\",\"weight\":70.0}", 999999L);

        ChangeEvent good2 = buildEvent(UUID.randomUUID(), patient3, ChangeType.CREATE,
                "{\"patientName\":\"Charlie\",\"weight\":75.0}", null);

        SyncPushRequest batch = new SyncPushRequest("tablet-alpha", List.of(good1, bad, good2));

        SyncPushResponse response = syncService.push(batch);

        // Verify outcomes in API response
        assertEquals(2, response.acceptedCount(), "2 good events should be accepted");
        assertEquals(1, response.rejectedEvents().size(), "1 bad event should be rejected");
        assertEquals(bad.getEventId(), response.rejectedEvents().get(0).eventId());

        // Verify actual database state in PostgreSQL
        assertTrue(changeEventRepository.existsById(good1.getEventId()), "Event 1 must be committed in PostgreSQL");
        assertTrue(changeEventRepository.existsById(good2.getEventId()), "Event 3 must be committed in PostgreSQL");
        assertFalse(changeEventRepository.existsById(bad.getEventId()), "Event 2 must be completely rolled back from PostgreSQL");

        assertTrue(patientRecordRepository.existsById(patient1), "Alice must be projected into patient_record");
        assertTrue(patientRecordRepository.existsById(patient3), "Charlie must be projected into patient_record");
        assertFalse(patientRecordRepository.existsById(patient2), "Bob must NOT exist in patient_record");
    }

    @Test
    void idempotentRepush_duplicateSubmissionDoesNotDuplicateRowsInDatabase() {
        UUID patientId = UUID.randomUUID();
        ChangeEvent event = buildEvent(UUID.randomUUID(), patientId, ChangeType.CREATE,
                "{\"patientName\":\"David\",\"weight\":80.0}", null);

        SyncPushRequest firstPush = new SyncPushRequest("tablet-alpha", List.of(event));
        SyncPushResponse firstResponse = syncService.push(firstPush);
        assertEquals(1, firstResponse.acceptedCount());

        long countAfterFirst = changeEventRepository.count();

        // Simulate network disconnect: client retries the exact same push
        SyncPushResponse retryResponse = syncService.push(firstPush);

        assertEquals(0, retryResponse.acceptedCount(), "Duplicate event should be skipped");
        assertEquals(0, retryResponse.rejectedEvents().size());
        assertEquals(countAfterFirst, changeEventRepository.count(), "Row count in PostgreSQL must not change on retry");
    }

    @Test
    void gaplessPull_deviceFetchesOnlyCommittedChangesInOrder() {
        UUID patientId = UUID.randomUUID();
        ChangeEvent e1 = buildEvent(UUID.randomUUID(), patientId, ChangeType.CREATE,
                "{\"patientName\":\"Eva\",\"weight\":55.0}", null);
        ChangeEvent e2 = buildEvent(UUID.randomUUID(), patientId, ChangeType.UPDATE,
                "{\"weight\":56.5}", null);

        syncService.push(new SyncPushRequest("tablet-alpha", List.of(e1, e2)));

        // Pull from cursor 0
        SyncPullResponse pullResponse = syncService.pull(new SyncPullRequest("tablet-beta", 0L, 500));

        assertEquals(2, pullResponse.changes().size());
        assertEquals(e1.getEventId(), pullResponse.changes().get(0).getEventId());
        assertEquals(e2.getEventId(), pullResponse.changes().get(1).getEventId());
        assertTrue(pullResponse.nextCursor() > 0, "nextCursor must advance to highest server_seq");
    }
}