package com.balaji.sync_engine.repository;

import com.balaji.sync_engine.conflict.ConflictStatus;
import com.balaji.sync_engine.entity.ConflictRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConflictRecordRepository extends JpaRepository<ConflictRecord, UUID> {
    Page<ConflictRecord> findByStatus(ConflictStatus status, Pageable pageable);
}