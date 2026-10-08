package com.balaji.sync_engine.repository;

import com.balaji.sync_engine.entity.PatientRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientRecordRepository extends JpaRepository<PatientRecord, UUID> {
	
	List<PatientRecord> findByBloodPressure(String bloodPressure);
	// PatientRecordRepository.java — change signature to accept Pageable
	Page<PatientRecord> findAllByDeletedAtIsNull(Pageable pageable);
}
