package com.balaji.sync_engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "entity_deletion_state", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"entity_type", "entity_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class EntityDeletionState {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String deletionHlc;

    @Column(nullable = false)
    private String deletedByDeviceId;
}