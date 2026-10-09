package com.balaji.sync_engine.entity;

import com.balaji.sync_engine.conflict.ConflictStatus;
import com.balaji.sync_engine.conflict.ConflictType;
import com.balaji.sync_engine.conflict.ResolutionAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conflict_record", indexes = {
        @Index(name = "idx_conflict_status_detected", columnList = "status, detected_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ConflictRecord {

    @Id
    @GeneratedValue
    private UUID id;

    /** Optimistic lock: a second concurrent resolver fails at commit instead of double-applying. */
    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConflictType conflictType;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    /** The incoming event that raised this conflict. */
    @Column(nullable = false)
    private UUID eventId;

    private String fieldName;

    @Column(columnDefinition = "text")
    private String currentValue;
    private String currentDeviceId;

    @Column(columnDefinition = "text")
    private String incomingValue;
    private String incomingDeviceId;

    @Column(columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConflictStatus status = ConflictStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private ResolutionAction resolution;

    @Column(columnDefinition = "text")
    private String resolvedValue;

    /** The ChangeEvent that propagated the decision to devices. Audit link. */
    private UUID resolutionEventId;

    private String resolvedBy;
    private Instant resolvedAt;

    @Column(nullable = false, updatable = false)
    private Instant detectedAt;

    @PrePersist
    protected void onCreate() {
        this.detectedAt = Instant.now();
    }
}