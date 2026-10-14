package com.balaji.sync_engine;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.conflict.ConflictStatus;
import com.balaji.sync_engine.conflict.ResolutionAction;
import com.balaji.sync_engine.dto.ResolveConflictRequest;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.entity.ConflictRecord;
import com.balaji.sync_engine.entity.PatientRecord;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.ConflictRecordRepository;
import com.balaji.sync_engine.repository.FieldStateRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import com.balaji.sync_engine.service.ConflictReviewService;
import com.balaji.sync_engine.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.UUID;

class ConflictWorkflowIT extends BaseIntegrationTest {

    @Autowired private SyncService syncService;
    @Autowired private ConflictReviewService conflictReviewService;
    @Autowired private ConflictRecordRepository conflictRecordRepository;
    @Autowired private PatientRecordRepository patientRecordRepository;
    @Autowired private FieldStateRepository fieldStateRepository;
    @Autowired private ChangeEventRepository changeEventRepository;
    @Autowired private HybridLogicalClock clock;

    @BeforeEach
    void cleanUp() {
        conflictRecordRepository.deleteAll();
        fieldStateRepository.deleteAll();
        patientRecordRepository.deleteAll();
        changeEventRepository.deleteAll();
    }

    @Test
    void fieldConflictDetectionAndSupervisoryResolutionLifecycle() {
        UUID patientId = UUID.randomUUID();

        // 1. Device A creates patient with dosage = "250mg"
        ChangeEvent create = new ChangeEvent();
        create.setEventId(UUID.randomUUID());
        create.setEntityType("PatientRecord");
        create.setEntityId(patientId);
        create.setChangeType(ChangeType.CREATE);
        create.setDeviceId("tablet-a");
        create.setHlcTimestamp(clock.tick().toString());
        create.setPayload("{\"patientName\":\"Frank\",\"dosage\":\"250mg\"}");

        syncService.push(new SyncPushRequest("tablet-a", List.of(create)));

        // 2. Device B (offline, never pulled Device A's write, basedOnSeq = null) updates dosage = "500mg"
        ChangeEvent conflictUpdate = new ChangeEvent();
        conflictUpdate.setEventId(UUID.randomUUID());
        conflictUpdate.setEntityType("PatientRecord");
        conflictUpdate.setEntityId(patientId);
        conflictUpdate.setChangeType(ChangeType.UPDATE);
        conflictUpdate.setDeviceId("tablet-b");
        conflictUpdate.setHlcTimestamp(clock.tick().toString());
        conflictUpdate.setBasedOnSeq(null); // Offline collision!
        conflictUpdate.setPayload("{\"dosage\":\"500mg\"}");

        SyncPushResponse pushResponse = syncService.push(new SyncPushRequest("tablet-b", List.of(conflictUpdate)));

        // Verify conflict was flagged
        assertEquals(1, pushResponse.conflicts().size());
        UUID conflictId = pushResponse.conflicts().get(0).conflictId();
        assertNotNull(conflictId);

        // Verify conflict_record in PostgreSQL
        ConflictRecord storedConflict = conflictRecordRepository.findById(conflictId).orElseThrow();
        assertEquals(ConflictStatus.PENDING, storedConflict.getStatus());
        assertEquals("250mg", storedConflict.getCurrentValue());
        assertEquals("500mg", storedConflict.getIncomingValue());

        // Verify dosage was NOT overwritten yet
        PatientRecord beforeResolve = patientRecordRepository.findById(patientId).orElseThrow();
        assertEquals("250mg", beforeResolve.getDosage());

        // 3. Supervisor resolves conflict with CUSTOM_VALUE = "375mg"
        ResolveConflictRequest resolveReq = new ResolveConflictRequest(ResolutionAction.CUSTOM_VALUE, "375mg");
        ConflictRecord resolvedRecord = conflictReviewService.resolve(conflictId, resolveReq, "supervisor_mary");

        // Verify state after resolution
        assertEquals(ConflictStatus.RESOLVED, resolvedRecord.getStatus());
        assertEquals("375mg", resolvedRecord.getResolvedValue());

        // Verify patient_record was updated in PostgreSQL
        PatientRecord afterResolve = patientRecordRepository.findById(patientId).orElseThrow();
        assertEquals("375mg", afterResolve.getDosage());

        // Verify a server-originated event was logged in change_event for devices to pull
        assertTrue(changeEventRepository.existsById(resolvedRecord.getResolutionEventId()));
    }

    @Test
    void optimisticLocking_preventsDoubleResolutionCollision() {
        UUID patientId = UUID.randomUUID();

        // 1. Create patient record first so the projector does not violate patient_name NOT NULL constraint
        PatientRecord patient = new PatientRecord();
        patient.setId(patientId);
        patient.setPatientName("Test Patient");
        patientRecordRepository.save(patient);

        // 2. Create a pending conflict
        ConflictRecord conflict = new ConflictRecord();
        conflict.setConflictType(com.balaji.sync_engine.conflict.ConflictType.FIELD_CONFLICT);
        conflict.setEntityType("PatientRecord");
        conflict.setEntityId(patientId);
        conflict.setEventId(UUID.randomUUID());
        conflict.setFieldName("dosage");
        conflict.setCurrentValue("100mg");
        conflict.setIncomingValue("200mg");
        conflict.setStatus(ConflictStatus.PENDING);
        ConflictRecord saved = conflictRecordRepository.save(conflict);

        // 3. First supervisor resolves
        ResolveConflictRequest req1 = new ResolveConflictRequest(ResolutionAction.KEEP_CURRENT, null);
        conflictReviewService.resolve(saved.getId(), req1, "supervisor_1");

        // 4. Second supervisor tries to resolve the stale entity instance
        ResolveConflictRequest req2 = new ResolveConflictRequest(ResolutionAction.ACCEPT_INCOMING, null);

        assertThrows(Exception.class, () -> {
            conflictReviewService.resolve(saved.getId(), req2, "supervisor_2");
        }, "Second concurrent resolution must be blocked by status or version check");
    }
}