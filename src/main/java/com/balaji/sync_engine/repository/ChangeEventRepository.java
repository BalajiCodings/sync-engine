package com.balaji.sync_engine.repository;

import com.balaji.sync_engine.entity.ChangeEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChangeEventRepository extends JpaRepository<ChangeEvent, UUID> {

    List<ChangeEvent> findByEntityTypeAndEntityIdOrderByHlcTimestampAsc(String entityType, UUID entityId);

    List<ChangeEvent> findByServerSeqGreaterThanOrderByServerSeqAsc(long serverSeq, Pageable pageable);
}