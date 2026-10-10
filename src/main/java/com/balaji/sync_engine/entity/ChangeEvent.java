package com.balaji.sync_engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "change_event", indexes = {
        @Index(name = "idx_change_event_server_seq", columnList = "server_seq", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class ChangeEvent implements Persistable<UUID> {

    @Id
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
    
    /** Position in the server's event log. Assigned by Postgres at insert time. This is the pull cursor. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "server_seq", insertable = false, updatable = false, columnDefinition = "bigserial")
    private Long serverSeq;

    @Transient
    private boolean isNew = true;

    @PrePersist
    protected void onCreate() {
        this.serverReceivedAt = Instant.now();
    }

    @JsonIgnore
    @Override
    public UUID getId() {
        return eventId;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}