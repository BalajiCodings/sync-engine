package com.balaji.sync_engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_record")
@Getter
@Setter
@NoArgsConstructor
public class PatientRecord implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String patientName;

    private Double weight;

    private String bloodPressure;

    private String dosage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private boolean isNew = true;
    
 // Add to PatientRecord.java
    private Instant deletedAt;
    
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long dosesAdministered;


    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return isNew;
    }

    @JsonIgnore
    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}