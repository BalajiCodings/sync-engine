package com.balaji.sync_engine.service;

import com.balaji.sync_engine.entity.FieldState;
import com.balaji.sync_engine.entity.PatientRecord;
import com.balaji.sync_engine.repository.FieldStateRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PatientRecordProjector {

    private static final String ENTITY_TYPE = "PatientRecord";

    private final PatientRecordRepository patientRepository;
    private final FieldStateRepository fieldStateRepository;

    public PatientRecordProjector(PatientRecordRepository patientRepository,
                                   FieldStateRepository fieldStateRepository) {
        this.patientRepository = patientRepository;
        this.fieldStateRepository = fieldStateRepository;
    }

    @Transactional
    public void project(UUID entityId) {
        List<FieldState> fields = fieldStateRepository.findByEntityTypeAndEntityId(ENTITY_TYPE, entityId);
        if (fields.isEmpty()) {
            return;
        }

        PatientRecord record = patientRepository.findById(entityId).orElseGet(() -> {
            PatientRecord fresh = new PatientRecord();
            fresh.setId(entityId);
            return fresh;
        });

        for (FieldState field : fields) {
            applyField(record, field.getFieldName(), field.getFieldValue());
        }

        patientRepository.save(record);
    }

    @Transactional
    public void remove(UUID entityId) {
        patientRepository.deleteById(entityId);
    }

    private void applyField(PatientRecord record, String fieldName, String value) {
        switch (fieldName) {
            case "patientName" -> record.setPatientName(value);
            case "weight" -> record.setWeight(value == null ? null : Double.parseDouble(value));
            case "bloodPressure" -> record.setBloodPressure(value);
            case "dosage" -> record.setDosage(value);
            default -> { /* unmapped field — ignored for now */ }
        }
    }
}