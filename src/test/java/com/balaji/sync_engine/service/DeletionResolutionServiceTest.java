package com.balaji.sync_engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.EntityDeletionState;
import com.balaji.sync_engine.repository.EntityDeletionStateRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeletionResolutionServiceTest {

    @Mock private EntityDeletionStateRepository deletionRepository;

    private DeletionResolutionService service;

    @BeforeEach
    void setUp() {
        service = new DeletionResolutionService(deletionRepository);
    }

    private static String hlc(long physical, String node) {
        return new HLCTimestamp(physical, 0, node).toString();
    }

    @Test
    void checkBeforeUpdate_whenNotDeleted_returnsNotDeleted() {
        UUID entityId = UUID.randomUUID();
        ChangeEvent update = new ChangeEvent();
        update.setEntityType("PatientRecord");
        update.setEntityId(entityId);

        when(deletionRepository.findByEntityTypeAndEntityId("PatientRecord", entityId))
                .thenReturn(Optional.empty());

        DeletionResolutionService.DeletionCheckResult res = service.checkBeforeUpdate(update);
        assertFalse(res.isDeleted());
        assertFalse(res.restored());
    }

    @Test
    void checkBeforeUpdate_differentDeviceWithoutSeeingDeletion_raisesConflict() {
        UUID entityId = UUID.randomUUID();
        EntityDeletionState state = new EntityDeletionState();
        state.setEntityType("PatientRecord");
        state.setEntityId(entityId);
        state.setDeletedByDeviceId("dev-a");
        state.setDeletionHlc(hlc(100, "dev-a"));
        state.setDeletionSeq(10L);

        ChangeEvent update = new ChangeEvent();
        update.setEntityType("PatientRecord");
        update.setEntityId(entityId);
        update.setDeviceId("dev-b");
        update.setHlcTimestamp(hlc(200, "dev-b"));
        update.setBasedOnSeq(5L); // has only seen up to seq 5, missed deletion at seq 10

        when(deletionRepository.findByEntityTypeAndEntityId("PatientRecord", entityId))
                .thenReturn(Optional.of(state));

        DeletionResolutionService.DeletionCheckResult res = service.checkBeforeUpdate(update);
        assertTrue(res.isDeleted());
        assertFalse(res.restored());
        assertTrue(res.reason().contains("DELETE_UPDATE_CONFLICT"));
        verify(deletionRepository, never()).deleteByEntityTypeAndEntityId(any(), any());
    }

    @Test
    void checkBeforeUpdate_differentDeviceThatObservedDeletion_restoresEntity() {
        UUID entityId = UUID.randomUUID();
        EntityDeletionState state = new EntityDeletionState();
        state.setEntityType("PatientRecord");
        state.setEntityId(entityId);
        state.setDeletedByDeviceId("dev-a");
        state.setDeletionHlc(hlc(100, "dev-a"));
        state.setDeletionSeq(10L);

        ChangeEvent update = new ChangeEvent();
        update.setEntityType("PatientRecord");
        update.setEntityId(entityId);
        update.setDeviceId("dev-b");
        update.setHlcTimestamp(hlc(200, "dev-b"));
        update.setBasedOnSeq(10L); // saw the deletion at seq 10

        when(deletionRepository.findByEntityTypeAndEntityId("PatientRecord", entityId))
                .thenReturn(Optional.of(state));

        DeletionResolutionService.DeletionCheckResult res = service.checkBeforeUpdate(update);
        assertFalse(res.isDeleted());
        assertTrue(res.restored());
        verify(deletionRepository).deleteByEntityTypeAndEntityId("PatientRecord", entityId);
    }

    @Test
    void checkBeforeUpdate_sameDeviceWithLaterHlc_restoresEntity() {
        UUID entityId = UUID.randomUUID();
        EntityDeletionState state = new EntityDeletionState();
        state.setEntityType("PatientRecord");
        state.setEntityId(entityId);
        state.setDeletedByDeviceId("dev-a");
        state.setDeletionHlc(hlc(100, "dev-a"));
        state.setDeletionSeq(10L);

        ChangeEvent update = new ChangeEvent();
        update.setEntityType("PatientRecord");
        update.setEntityId(entityId);
        update.setDeviceId("dev-a");
        update.setHlcTimestamp(hlc(101, "dev-a"));

        when(deletionRepository.findByEntityTypeAndEntityId("PatientRecord", entityId))
                .thenReturn(Optional.of(state));

        DeletionResolutionService.DeletionCheckResult res = service.checkBeforeUpdate(update);
        assertFalse(res.isDeleted());
        assertTrue(res.restored());
        verify(deletionRepository).deleteByEntityTypeAndEntityId("PatientRecord", entityId);
    }
}