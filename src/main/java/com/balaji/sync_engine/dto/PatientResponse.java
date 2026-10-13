package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.entity.PatientRecord;

import java.time.Instant;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String patientName,
        Double weight,
        String bloodPressure,
        String dosage,
        Long dosesAdministered,
        Instant createdAt,
        Instant updatedAt
) {
    public static PatientResponse from(PatientRecord record) {
        return new PatientResponse(
                record.getId(),
                record.getPatientName(),
                record.getWeight(),
                record.getBloodPressure(),
                record.getDosage(),
                record.getDosesAdministered(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}