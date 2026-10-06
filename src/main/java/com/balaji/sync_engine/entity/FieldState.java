package com.balaji.sync_engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "field_state", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"entity_type", "entity_id", "field_name"})
})
@Getter
@Setter
@NoArgsConstructor
public class FieldState {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String fieldName;

    @Column(columnDefinition = "text")
    private String fieldValue;

    @Column(nullable = false)
    private String lastWriteHlc;

    @Column(nullable = false)
    private String lastWriteDeviceId;
}