package com.balaji.sync_engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdatePatientRequest(
        @NotBlank(message = "patientName is required and cannot be blank")
        @Size(max = 255, message = "patientName cannot exceed 255 characters")
        String patientName,

        @Positive(message = "weight must be a positive number")
        Double weight,

        @Pattern(regexp = "^$|^[0-9]{2,3}/[0-9]{2,3}$", message = "bloodPressure must be in format 'systolic/diastolic', e.g. '120/80'")
        String bloodPressure,

        @Size(max = 100, message = "dosage cannot exceed 100 characters")
        String dosage
) {}