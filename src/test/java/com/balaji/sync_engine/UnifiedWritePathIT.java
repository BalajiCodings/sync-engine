package com.balaji.sync_engine;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.entity.PatientRecord;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.EntityDeletionStateRepository;
import com.balaji.sync_engine.repository.FieldStateRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import com.balaji.sync_engine.service.PatientRecordService;
import com.balaji.sync_engine.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

class UnifiedWritePathIT extends BaseIntegrationTest {

    @Autowired private PatientRecordService patientRecordService;
    @Autowired private SyncService syncService;
    @Autowired private PatientRecordRepository patientRecordRepository;
    @Autowired private FieldStateRepository fieldStateRepository;
    @Autowired private EntityDeletionStateRepository deletionStateRepository;
    @Autowired private ChangeEventRepository changeEventRepository;
    @Autowired private HybridLogicalClock clock;

    @BeforeEach
    void cleanUp() {
        deletionStateRepository.deleteAll();
        fieldStateRepository.deleteAll();
        patientRecordRepository.deleteAll();
        changeEventRepository.deleteAll();
    }

    @Test
    void restCreatedPatient_populatesFieldState_andIsNotRevertedByLaterSync() {
        // 1. Create patient via REST
        PatientRecord input = new PatientRecord();
        input.setPatientName("Grace Hopper");
        input.setWeight(58.0);
        input.setBloodPressure("118/75");
        input.setDosage("10mg");

        PatientRecord created = patientRecordService.create(input);
        UUID patientId = created.getId();

        // Verify Task #13 fix: field_state MUST have rows for this patient
        assertFalse(fieldStateRepository.findByEntityTypeAndEntityId("PatientRecord", patientId).isEmpty(),
                "REST create must write to field_state!");

        // 2. Mobile tablet pushes an update modifying ONLY 'weight' to 60.5
        ChangeEvent tabletUpdate = new ChangeEvent();
        tabletUpdate.setEventId(UUID.randomUUID());
        tabletUpdate.setEntityType("PatientRecord");
        tabletUpdate.setEntityId(patientId);
        tabletUpdate.setChangeType(ChangeType.UPDATE);
        tabletUpdate.setDeviceId("tablet-field-3");
        tabletUpdate.setHlcTimestamp(clock.tick().toString());
        tabletUpdate.setBasedOnSeq(changeEventRepository.findHeadSeq());
        tabletUpdate.setPayload("{\"weight\":60.5}");

        syncService.push(new SyncPushRequest("tablet-field-3", List.of(tabletUpdate)));

        // 3. Verify read model in PostgreSQL
        PatientRecord projected = patientRecordRepository.findById(patientId).orElseThrow();

        assertEquals(60.5, projected.getWeight(), "Weight should be updated from tablet");
        // CRITICAL CHECK: In the buggy code, dosage and patientName would be null/reverted here.
        assertEquals("Grace Hopper", projected.getPatientName(), "Name must NOT be reverted!");
        assertEquals("118/75", projected.getBloodPressure(), "Blood pressure must NOT be reverted!");
        assertEquals("10mg", projected.getDosage(), "Dosage must NOT be reverted!");
    }

    @Test
    void restDeletedPatient_populatesDeletionState() {
        PatientRecord record = new PatientRecord();
        record.setPatientName("Alan Turing");
        PatientRecord created = patientRecordService.create(record);

        patientRecordService.delete(created.getId());

        // Verify entity_deletion_state in PostgreSQL
        assertTrue(deletionStateRepository.findByEntityTypeAndEntityId("PatientRecord", created.getId()).isPresent(),
                "REST delete must record deletion state to detect delete-vs-update races");
    }
}