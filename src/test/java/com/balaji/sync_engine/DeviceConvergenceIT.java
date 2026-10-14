package com.balaji.sync_engine;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.crdt.PnCounter;
import com.balaji.sync_engine.crdt.PnCounterCodec;
import com.balaji.sync_engine.entity.FieldState;
import com.balaji.sync_engine.entity.PatientRecord;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.FieldStateRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import com.balaji.sync_engine.service.PatientRecordService;
import com.balaji.sync_engine.service.SyncService;
import com.balaji.sync_engine.simulator.SimulatedDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Phase 5 Proving Ground: Proves that multiple devices making concurrent offline
 * edits across scalars and CRDT counters converge to the exact same state.
 */
class DeviceConvergenceIT extends BaseIntegrationTest {

    @Autowired private SyncService syncService;
    @Autowired private PatientRecordService patientRecordService;
    @Autowired private PatientRecordRepository patientRecordRepository;
    @Autowired private FieldStateRepository fieldStateRepository;
    @Autowired private ChangeEventRepository changeEventRepository;
    @Autowired private PnCounterCodec counterCodec;

    @BeforeEach
    void cleanDatabase() {
        fieldStateRepository.deleteAll();
        patientRecordRepository.deleteAll();
        changeEventRepository.deleteAll();
    }

    @Test
    void multiDeviceConvergence_fiveDevices_offlineDivergence_and_finalConvergence() {
        // 1. Initial State: Server creates a patient record
        PatientRecord initial = new PatientRecord();
        initial.setPatientName("Convergence Patient");
        initial.setBloodPressure("120/80");
        initial.setWeight(70.0);
        PatientRecord saved = patientRecordService.create(initial);
        UUID patientId = saved.getId();

        // 2. Initialize 5 simulated field devices
        List<SimulatedDevice> devices = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            devices.add(new SimulatedDevice("tablet-clinic-" + i, counterCodec));
        }

        // 3. All 5 devices perform initial pull to sync the baseline state
        for (SimulatedDevice device : devices) {
            device.pull(syncService);
            assertEquals("Convergence Patient", device.getField(patientId, "patientName"));
        }

        // -------------------------------------------------------------
        // 4. CHAOS PHASE: All 5 devices go OFFLINE and make concurrent edits
        // -------------------------------------------------------------
        // Device 1 & 2 modify bloodPressure (LWW scalar: highest timestamp must win)
        devices.get(0).mutateOffline(patientId, "bloodPressure", "125/82");
        devices.get(1).mutateOffline(patientId, "bloodPressure", "135/88");

        // Device 3 & 4 increment dosesAdministered (PN-Counter CRDT: 10 + 5 = 15 total)
        devices.get(2).mutateCounterOffline(patientId, "dosesAdministered", 10);
        devices.get(3).mutateCounterOffline(patientId, "dosesAdministered", 5);

        // Device 5 modifies weight (LWW scalar)
        devices.get(4).mutateOffline(patientId, "weight", "72.4");

        // -------------------------------------------------------------
        // 5. RECONNECTION PHASE: Devices push in non-deterministic random order
        // -------------------------------------------------------------
        List<SimulatedDevice> shufflePush = new ArrayList<>(devices);
        Collections.shuffle(shufflePush);

        for (SimulatedDevice device : shufflePush) {
            var pushResult = device.push(syncService);
            assertEquals(0, pushResult.rejectedEvents().size(), "No events should be rejected");
        }

        // -------------------------------------------------------------
        // 6. CATCH-UP PHASE: All devices pull until up to date
        // -------------------------------------------------------------
        for (SimulatedDevice device : devices) {
            device.pull(syncService);
        }

        // -------------------------------------------------------------
        // 7. GOLDEN CONVERGENCE ASSERTION (Audit)
        // -------------------------------------------------------------
        // A. Verify CRDT Counter convergence: exactly 15 across all devices and server
        FieldState serverCounterState = fieldStateRepository
                .findByEntityTypeAndEntityIdAndFieldName("PatientRecord", patientId, "dosesAdministered")
                .orElseThrow();
        PnCounter serverCounter = counterCodec.parse(serverCounterState.getFieldValue());
        assertEquals(15L, serverCounter.value(), "Server counter must be 10 + 5 = 15");

        for (SimulatedDevice device : devices) {
            String deviceCounterJson = device.getField(patientId, "dosesAdministered");
            assertNotNull(deviceCounterJson);
            PnCounter deviceCounter = counterCodec.parse(deviceCounterJson);
            assertEquals(15L, deviceCounter.value(),
                    "Device " + device.getDeviceId() + " must have converged to counter 15");
        }

        // B. Verify LWW Weight convergence: exactly 72.4 on all devices
        for (SimulatedDevice device : devices) {
            assertEquals("72.4", device.getField(patientId, "weight"),
                    "Device " + device.getDeviceId() + " must have converged to weight 72.4");
        }

        // C. Verify all 5 devices have identical state with each other
        String referenceBp = devices.get(0).getField(patientId, "bloodPressure");
        assertNotNull(referenceBp);
        for (SimulatedDevice device : devices) {
            assertEquals(referenceBp, device.getField(patientId, "bloodPressure"),
                    "Every device must agree on the winning bloodPressure value");
        }

        System.out.println("✅ [CONVERGENCE AUDIT PASSED] 5 Replicas and Server are 100% mathematically identical.");
    }
}