package com.balaji.sync_engine.repository;

import com.balaji.sync_engine.entity.FieldState;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface FieldStateRepository extends JpaRepository<FieldState, UUID> {
    Optional<FieldState> findByEntityTypeAndEntityIdAndFieldName(String entityType, UUID entityId, String fieldName);
    List<FieldState> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}