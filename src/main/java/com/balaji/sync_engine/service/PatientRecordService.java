package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HybridLogicalClock;


import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.entity.PatientRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PatientRecordService {

    private static final String ENTITY_TYPE = "PatientRecord";
    private static final String SERVER_DEVICE_ID = "server";

    private final PatientRecordRepository patientRepository;
    private final ChangeEventRepository changeEventRepository;
    private final JsonMapper jsonMapper;
    private final HybridLogicalClock clock;

    public PatientRecordService(PatientRecordRepository patientRepository,
                                 ChangeEventRepository changeEventRepository,
                                 JsonMapper jsonMapper,
                                 HybridLogicalClock clock) {
        this.patientRepository = patientRepository;
        this.changeEventRepository = changeEventRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public PatientRecord create(PatientRecord record) {
        if (record.getId() == null) {
            record.setId(UUID.randomUUID());
        }
        PatientRecord saved = patientRepository.save(record);
        logChange(saved.getId(), ChangeType.CREATE, saved);
        return saved;
    }
    
    public List<PatientRecord> findAll() {
        return patientRepository.findAll();
    }

    public PatientRecord findById(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("PatientRecord not found: " + id));
    }

    @Transactional
    public PatientRecord update(UUID id, PatientRecord updated) {
        PatientRecord existing = findById(id);
        existing.setPatientName(updated.getPatientName());
        existing.setWeight(updated.getWeight());
        existing.setBloodPressure(updated.getBloodPressure());
        existing.setDosage(updated.getDosage());
        PatientRecord saved = patientRepository.save(existing);
        logChange(saved.getId(), ChangeType.UPDATE, saved);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        PatientRecord existing = findById(id);
        existing.setDeletedAt(java.time.Instant.now());
        patientRepository.save(existing);
        logChange(id, ChangeType.DELETE, existing);
    }
    
 // PatientRecordService.java
    public Page<PatientRecord> findAll(Pageable pageable) {
        return patientRepository.findAllByDeletedAtIsNull(pageable);
    }

    private void logChange(UUID entityId, ChangeType changeType, PatientRecord record) {
        ChangeEvent event = new ChangeEvent();
        event.setEventId(UUID.randomUUID());   // ← new line — we generate it explicitly now
        event.setEntityType(ENTITY_TYPE);
        event.setEntityId(entityId);
        event.setChangeType(changeType);
        event.setDeviceId(SERVER_DEVICE_ID);
        event.setHlcTimestamp(clock.tick().toString());
        event.setPayload(jsonMapper.writeValueAsString(record));
        changeEventRepository.save(event);
    }
}