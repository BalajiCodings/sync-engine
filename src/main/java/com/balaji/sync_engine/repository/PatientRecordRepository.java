package com.balaji.sync_engine.repository;

import com.balaji.sync_engine.entity.PatientRecord;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientRecordRepository extends JpaRepository<PatientRecord, UUID> {
	
	List<PatientRecord> findByBloodPressure(String bloodPressure);
}
