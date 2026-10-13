package com.balaji.sync_engine.service;

import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.entity.PatientRecord;
import com.balaji.sync_engine.repository.ChangeEventRepository;
import com.balaji.sync_engine.repository.PatientRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PatientRecordService {

    private static final String ENTITY_TYPE = "PatientRecord";
    private static final String SERVER_DEVICE_ID = "server";

    private final PatientRecordRepository patientRepository;
    private final ChangeEventRepository changeEventRepository;
    private final SyncEventProcessor syncEventProcessor;
    private final JsonMapper jsonMapper;
    private final HybridLogicalClock clock;

    public PatientRecordService(PatientRecordRepository patientRepository,
                                ChangeEventRepository changeEventRepository,
                                SyncEventProcessor syncEventProcessor,
                                JsonMapper jsonMapper,
                                HybridLogicalClock clock) {
        this.patientRepository = patientRepository;
        this.changeEventRepository = changeEventRepository;
        this.syncEventProcessor = syncEventProcessor;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public PatientRecord create(PatientRecord record) {
        if (record.getPatientName() == null || record.getPatientName().isBlank()) {
            throw new IllegalArgumentException("patientName is required");
        }
        UUID id = record.getId() != null ? record.getId() : UUID.randomUUID();
        ChangeEvent event = buildServerEvent(id, ChangeType.CREATE, toFieldMap(record));
        syncEventProcessor.processEvent(event);
        return findById(id);
    }

    @Transactional(readOnly = true)
    public List<PatientRecord> findAll() {
        return patientRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<PatientRecord> findAll(Pageable pageable) {
        return patientRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public PatientRecord findById(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("PatientRecord not found: " + id));
    }

    @Transactional
    public PatientRecord update(UUID id, PatientRecord updated) {
        findById(id); // Ensure 404 if record does not exist
        if (updated.getPatientName() == null || updated.getPatientName().isBlank()) {
            throw new IllegalArgumentException("patientName is required");
        }
        ChangeEvent event = buildServerEvent(id, ChangeType.UPDATE, toFieldMap(updated));
        syncEventProcessor.processEvent(event);
        return findById(id);
    }

    @Transactional
    public void delete(UUID id) {
        findById(id); // Ensure 404 if record does not exist
        ChangeEvent event = buildServerEvent(id, ChangeType.DELETE, null);
        syncEventProcessor.processEvent(event);
    }

    private ChangeEvent buildServerEvent(UUID entityId, ChangeType changeType, Map<String, Object> fields) {
        ChangeEvent event = new ChangeEvent();
        event.setEventId(UUID.randomUUID());
        event.setEntityType(ENTITY_TYPE);
        event.setEntityId(entityId);
        event.setChangeType(changeType);
        event.setDeviceId(SERVER_DEVICE_ID);
        event.setHlcTimestamp(clock.tick().toString());
        event.setBasedOnSeq(changeEventRepository.findHeadSeq());
        event.setPayload(fields == null ? "{}" : jsonMapper.writeValueAsString(fields));
        return event;
    }

    private Map<String, Object> toFieldMap(PatientRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("patientName", record.getPatientName());
        map.put("weight", record.getWeight());
        map.put("bloodPressure", record.getBloodPressure());
        map.put("dosage", record.getDosage());
        return map;
    }
}