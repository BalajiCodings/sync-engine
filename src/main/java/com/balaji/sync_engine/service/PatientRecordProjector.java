package com.balaji.sync_engine.service;

import com.balaji.sync_engine.crdt.PnCounterCodec;
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
    private final PnCounterCodec counterCodec;

    public PatientRecordProjector(PatientRecordRepository patientRepository,
                                  FieldStateRepository fieldStateRepository,
                                  PnCounterCodec counterCodec) {
        this.patientRepository = patientRepository;
        this.fieldStateRepository = fieldStateRepository;
        this.counterCodec = counterCodec;
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
            case "dosesAdministered" -> record.setDosesAdministered(
                    value == null ? null : counterCodec.parse(value).value());
            default -> { /* unmapped field — ignored for now */ }
        }
    }
    
    @Transactional
    public void restore(UUID entityId) {
        patientRepository.findById(entityId).ifPresent(record -> {
            record.setDeletedAt(null);
            patientRepository.save(record);
        });
        project(entityId);
    }
    
    @Transactional
    public void markDeleted(UUID entityId) {
        patientRepository.findById(entityId).ifPresent(record -> {
            record.setDeletedAt(java.time.Instant.now());
            patientRepository.save(record);
        });
    }
}