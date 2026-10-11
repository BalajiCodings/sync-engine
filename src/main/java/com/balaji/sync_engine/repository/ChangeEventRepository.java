package com.balaji.sync_engine.repository;

import com.balaji.sync_engine.entity.ChangeEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChangeEventRepository extends JpaRepository<ChangeEvent, UUID> {

    List<ChangeEvent> findByEntityTypeAndEntityIdOrderByHlcTimestampAsc(String entityType, UUID entityId);

    List<ChangeEvent> findByServerSeqGreaterThanOrderByServerSeqAsc(long serverSeq, Pageable pageable);

    /** Reads the DB-assigned position of an already-flushed event (Hibernate never populates server_seq itself). */
    @Query("select e.serverSeq from ChangeEvent e where e.eventId = :eventId")
    Long findServerSeq(@Param("eventId") UUID eventId);

    /** Highest position in the log; 0 when empty. */
    @Query("select coalesce(max(e.serverSeq), 0L) from ChangeEvent e")
    long findHeadSeq();
}