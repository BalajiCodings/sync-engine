package com.balaji.sync_engine.entity;

import jakarta.persistence.*;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_record")
@Getter
@Setter
@NoArgsConstructor
public class PatientRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String patientName;

    private Double weight;

    private String bloodPressure;

    private String dosage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
