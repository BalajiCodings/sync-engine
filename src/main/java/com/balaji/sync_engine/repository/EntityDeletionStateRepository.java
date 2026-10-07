package com.balaji.sync_engine.repository;

import com.balaji.sync_engine.entity.EntityDeletionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EntityDeletionStateRepository extends JpaRepository<EntityDeletionState, UUID> {
    Optional<EntityDeletionState> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    void deleteByEntityTypeAndEntityId(String entityType, UUID entityId);
}