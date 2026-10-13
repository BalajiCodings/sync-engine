package com.balaji.sync_engine.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.balaji.sync_engine.conflict.ConflictType;
import com.balaji.sync_engine.dto.ConflictInfo;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncEventProcessorTest {

    @Mock private ChangeEventRepository changeEventRepository;
    @Mock private ConflictResolutionService conflictResolutionService;
    @Mock private DeletionResolutionService deletionResolutionService;
    @Mock private PatientRecordProjector projector;
    @Mock private ConflictReviewService conflictReviewService;
    @Mock private IngestionLock ingestionLock;
    @Mock private ChangeLogNotifier changeLogNotifier;

    private SyncEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SyncEventProcessor(
                changeEventRepository,
                conflictResolutionService,
                deletionResolutionService,
                projector,
                conflictReviewService,
                ingestionLock,
                changeLogNotifier);
    }

    private ChangeEvent sampleEvent(ChangeType type) {
        ChangeEvent e = new ChangeEvent();
        e.setEventId(UUID.randomUUID());
        e.setEntityType("PatientRecord");
        e.setEntityId(UUID.randomUUID());
        e.setChangeType(type);
        e.setDeviceId("dev-1");
        e.setHlcTimestamp("0000001700000000000-0000000000-dev-1");
        e.setPayload("{\"patientName\":\"Asha\",\"weight\":60.0}");
        return e;
    }

    @Test
    void duplicateEventIdIsSkippedWithoutTouchingState() {
        ChangeEvent incoming = sampleEvent(ChangeType.UPDATE);
        when(changeEventRepository.existsById(incoming.getEventId())).thenReturn(true);

        SyncEventProcessor.EventOutcome outcome = processor.processEvent(incoming);

        assertInstanceOf(SyncEventProcessor.Skipped.class, outcome);
        verify(ingestionLock).acquire();
        verify(changeEventRepository, never()).saveAndFlush(any());
        verifyNoInteractions(conflictResolutionService, deletionResolutionService, projector, changeLogNotifier);
    }

    @Test
    void basedOnSeqAheadOfServerLogThrowsIllegalArgument() {
        ChangeEvent incoming = sampleEvent(ChangeType.UPDATE);
        incoming.setBasedOnSeq(50L);
        when(changeEventRepository.existsById(incoming.getEventId())).thenReturn(false);
        when(changeEventRepository.findHeadSeq()).thenReturn(10L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> processor.processEvent(incoming));

        assertTrue(ex.getMessage().contains("ahead of the server log"));
        verify(changeEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteEventRecordsDeletionAndMarksProjectedEntityDeleted() {
        ChangeEvent incoming = sampleEvent(ChangeType.DELETE);
        when(changeEventRepository.existsById(incoming.getEventId())).thenReturn(false);
        when(changeEventRepository.findServerSeq(incoming.getEventId())).thenReturn(12L);
        when(conflictReviewService.recordDetected(anyList())).thenReturn(List.of());

        SyncEventProcessor.EventOutcome outcome = processor.processEvent(incoming);

        assertInstanceOf(SyncEventProcessor.Applied.class, outcome);
        InOrder inOrder = inOrder(ingestionLock, changeEventRepository, deletionResolutionService, projector, changeLogNotifier);
        inOrder.verify(ingestionLock).acquire();
        inOrder.verify(changeEventRepository).saveAndFlush(incoming);
        inOrder.verify(deletionResolutionService).recordDeletion(incoming, 12L);
        inOrder.verify(projector).markDeleted(incoming.getEntityId());
        inOrder.verify(changeLogNotifier).appended("dev-1");
    }

    @Test
    void updateAgainstDeletedEntityRaisesDeleteUpdateConflictAndSkipsFieldMerge() {
        ChangeEvent incoming = sampleEvent(ChangeType.UPDATE);
        when(changeEventRepository.existsById(incoming.getEventId())).thenReturn(false);
        when(changeEventRepository.findServerSeq(incoming.getEventId())).thenReturn(15L);
        when(deletionResolutionService.checkBeforeUpdate(incoming))
                .thenReturn(new DeletionResolutionService.DeletionCheckResult(true, false, "Deleted by dev-2"));
        when(conflictReviewService.recordDetected(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SyncEventProcessor.EventOutcome outcome = processor.processEvent(incoming);

        SyncEventProcessor.Applied applied = assertInstanceOf(SyncEventProcessor.Applied.class, outcome);
        assertEquals(1, applied.conflicts().size());
        assertEquals(ConflictType.DELETE_UPDATE_CONFLICT, applied.conflicts().get(0).conflictType());
        verifyNoInteractions(conflictResolutionService, projector);
        verify(changeLogNotifier).appended("dev-1");
    }

    @Test
    void normalUpdateAppliesFieldChangesAndProjectsReadModel() {
        ChangeEvent incoming = sampleEvent(ChangeType.UPDATE);
        when(changeEventRepository.existsById(incoming.getEventId())).thenReturn(false);
        when(changeEventRepository.findServerSeq(incoming.getEventId())).thenReturn(20L);
        when(deletionResolutionService.checkBeforeUpdate(incoming))
                .thenReturn(DeletionResolutionService.DeletionCheckResult.notDeleted());
        when(conflictResolutionService.applyIncomingChange(incoming, 20L))
                .thenReturn(new ConflictResolutionService.ApplyResult(List.of()));
        when(conflictReviewService.recordDetected(anyList())).thenReturn(List.of());

        SyncEventProcessor.EventOutcome outcome = processor.processEvent(incoming);

        assertInstanceOf(SyncEventProcessor.Applied.class, outcome);
        verify(conflictResolutionService).applyIncomingChange(incoming, 20L);
        verify(projector).project(incoming.getEntityId());
        verify(changeLogNotifier).appended("dev-1");
    }

    @Test
    void deliberateRestoreAfterDeletionCallsProjectorRestore() {
        ChangeEvent incoming = sampleEvent(ChangeType.UPDATE);
        when(changeEventRepository.existsById(incoming.getEventId())).thenReturn(false);
        when(changeEventRepository.findServerSeq(incoming.getEventId())).thenReturn(21L);
        when(deletionResolutionService.checkBeforeUpdate(incoming))
                .thenReturn(new DeletionResolutionService.DeletionCheckResult(false, true, null));
        when(conflictResolutionService.applyIncomingChange(incoming, 21L))
                .thenReturn(new ConflictResolutionService.ApplyResult(List.of()));
        when(conflictReviewService.recordDetected(anyList())).thenReturn(List.of());

        processor.processEvent(incoming);

        verify(projector).restore(incoming.getEntityId());
        verify(projector, never()).project(incoming.getEntityId());
    }
}