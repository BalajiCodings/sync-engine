package com.balaji.sync_engine.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "change_event")
@Getter
@Setter
@NoArgsConstructor
public class ChangeEvent {

    @Id
    @GeneratedValue
    private UUID eventId;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeType changeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private String hlcTimestamp;

    @Column(nullable = false, updatable = false)
    private Instant serverReceivedAt;

    @PrePersist
    protected void onCreate() {
        this.serverReceivedAt = Instant.now();
    }
}
