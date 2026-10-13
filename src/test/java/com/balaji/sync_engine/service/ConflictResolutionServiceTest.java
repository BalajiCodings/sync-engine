package com.balaji.sync_engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.conflict.MergeStrategyRegistry;
import com.balaji.sync_engine.crdt.PnCounterCodec;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.entity.FieldState;
import com.balaji.sync_engine.repository.FieldStateRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ConflictResolutionServiceTest {

    @Mock private FieldStateRepository fieldStateRepository;

    private ConflictResolutionService service;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        PnCounterCodec codec = new PnCounterCodec(jsonMapper);
        MergeStrategyRegistry registry = new MergeStrategyRegistry(codec);
        service = new ConflictResolutionService(fieldStateRepository, registry, jsonMapper);
    }

    private static String hlc(long physical, String node) {
        return new HLCTimestamp(physical, 0, node).toString();
    }

    @Test
    void untrackedMetadataFieldsAreIgnored() {
        ChangeEvent incoming = new ChangeEvent();
        incoming.setEventId(UUID.randomUUID());
        incoming.setEntityType("PatientRecord");
        incoming.setEntityId(UUID.randomUUID());
        incoming.setChangeType(ChangeType.CREATE);
        incoming.setDeviceId("dev-1");
        incoming.setHlcTimestamp(hlc(100, "dev-1"));
        incoming.setPayload("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"createdAt\":\"2026-01-01T00:00:00Z\",\"weight\":65.5}");

        when(fieldStateRepository.findByEntityTypeAndEntityIdAndFieldName(any(), any(), eq("weight")))
                .thenReturn(Optional.empty());

        ConflictResolutionService.ApplyResult result = service.applyIncomingChange(incoming, 1L);

        assertTrue(result.conflicts().isEmpty());
        ArgumentCaptor<FieldState> captor = ArgumentCaptor.forClass(FieldState.class);
        verify(fieldStateRepository, times(1)).save(captor.capture());
        assertEquals("weight", captor.getValue().getFieldName());
        assertEquals("65.5", captor.getValue().getFieldValue());
    }

    @Test
    void concurrentEditOnDosageWithoutObservingCurrentRaisesFieldConflictAndKeepsExistingState() {
        UUID entityId = UUID.randomUUID();
        FieldState current = new FieldState();
        current.setEntityType("PatientRecord");
        current.setEntityId(entityId);
        current.setFieldName("dosage");
        current.setFieldValue("500mg");
        current.setLastWriteDeviceId("dev-a");
        current.setLastWriteHlc(hlc(100, "dev-a"));
        current.setLastWriteSeq(10L);

        ChangeEvent incoming = new ChangeEvent();
        incoming.setEventId(UUID.randomUUID());
        incoming.setEntityType("PatientRecord");
        incoming.setEntityId(entityId);
        incoming.setChangeType(ChangeType.UPDATE);
        incoming.setDeviceId("dev-b");
        incoming.setHlcTimestamp(hlc(200, "dev-b"));
        incoming.setBasedOnSeq(5L); // < 10L, so dev-b did NOT observe dev-a's write
        incoming.setPayload("{\"dosage\":\"750mg\"}");

        when(fieldStateRepository.findByEntityTypeAndEntityIdAndFieldName("PatientRecord", entityId, "dosage"))
                .thenReturn(Optional.of(current));

        ConflictResolutionService.ApplyResult result = service.applyIncomingChange(incoming, 11L);

        assertEquals(1, result.conflicts().size());
        assertEquals("dosage", result.conflicts().get(0).fieldName());
        assertEquals("500mg", result.conflicts().get(0).currentValue());
        assertEquals("750mg", result.conflicts().get(0).incomingValue());
        verify(fieldStateRepository, never()).save(any());
    }

    @Test
    void losingOlderLwwWriteDoesNotRewindProvenanceOrSeq() {
        UUID entityId = UUID.randomUUID();
        FieldState current = new FieldState();
        current.setEntityType("PatientRecord");
        current.setEntityId(entityId);
        current.setFieldName("weight");
        current.setFieldValue("72.0");
        current.setLastWriteDeviceId("dev-a");
        current.setLastWriteHlc(hlc(500, "dev-a"));
        current.setLastWriteSeq(20L);

        ChangeEvent olderIncoming = new ChangeEvent();
        olderIncoming.setEventId(UUID.randomUUID());
        olderIncoming.setEntityType("PatientRecord");
        olderIncoming.setEntityId(entityId);
        olderIncoming.setChangeType(ChangeType.UPDATE);
        olderIncoming.setDeviceId("dev-b");
        olderIncoming.setHlcTimestamp(hlc(100, "dev-b"));
        olderIncoming.setPayload("{\"weight\":68.0}");

        when(fieldStateRepository.findByEntityTypeAndEntityIdAndFieldName("PatientRecord", entityId, "weight"))
                .thenReturn(Optional.of(current));

        service.applyIncomingChange(olderIncoming, 25L);

        assertEquals("72.0", current.getFieldValue());
        assertEquals("dev-a", current.getLastWriteDeviceId());
        assertEquals(hlc(500, "dev-a"), current.getLastWriteHlc());
        assertEquals(20L, current.getLastWriteSeq());
    }
}