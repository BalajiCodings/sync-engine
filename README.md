# Sync Engine — Offline-First Data Sync Backend

A backend system enabling mobile/web clients to work fully offline and sync changes
reliably once connectivity returns, using Hybrid Logical Clocks for causal ordering
and field-level conflict resolution strategies to prevent silent data loss.

Built to solve real sync problems faced by field workers (healthcare, agriculture,
logistics) operating in low-connectivity regions.

## Problem

Most backend systems assume clients are always connected. This project solves:
- Safe offline writes with no data loss
- Deterministic conflict resolution when multiple offline devices edit the same data
- Bandwidth-efficient delta sync (not full dataset re-sync)
- Correct event ordering even when device clocks are wrong or unsynchronized

## Tech Stack

- Java 21, Spring Boot 4
- PostgreSQL (JSONB for flexible event payloads)
- Hibernate / Spring Data JPA
- JUnit 5
- Maven

## Architecture

(Diagram coming as the project progresses)

## Development Progress

- [x] Phase 1 — Core CRUD (Controller/Service/Repository layering)
- [x] Phase 2 — Change-event logging (event-sourcing foundation)
- [x] Phase 3 — Hybrid Logical Clocks for causal ordering
- [x] Phase 4 — Delta sync pull/push endpoints, idempotent retries
- [x] Phase 5 — Field-level conflict resolution engine (LWW, Append,
      ManualReview strategies) + read-model projection
- [ ] Phase 6 — Fix known gaps below, soft-delete handling, partial-sync hardening
- [ ] Phase 7 — Testing (JUnit, Mockito, Testcontainers)
- [ ] Phase 8 — Observability (Micrometer/Actuator)
- [ ] Phase 9 — CLI client simulator for multi-device demo

## Known Limitations (tracked for upcoming phases)

These were found through deliberate end-to-end testing, not left in by accident —
documented here rather than quietly patched, since surfacing them is part of the
engineering process:

1. **`ManualReviewStrategy` over-triggers.** It currently flags a conflict on
   *every* repeat write to a protected field (e.g. `dosage`), even from the same
   device with a causally later timestamp — not just genuinely concurrent writes.
   It should check HLC causal order first and only escalate true concurrency.
2. **Hard deletes lose history-adjacent state immediately.** `DELETE` removes the
   `patient_record` projection outright; the delete-vs-concurrent-update race
   (a device editing a record another device just deleted) isn't handled yet.
3. **Single entity type.** `MergeStrategyRegistry` and `PatientRecordProjector`
   are currently hardcoded to `PatientRecord`'s fields. Generalizing to multiple
   entity types needs a proper `Projector`/strategy-lookup abstraction.
4. **`SyncPushResponse.conflictedEventIds` is misnamed** — it currently holds
   human-readable conflict reason strings, not event IDs. Needs renaming or a
   proper structured conflict DTO.
5. **No snapshot sync for new devices.** A brand-new device's first pull replays
   the *entire* event history rather than a compact current-state snapshot —
   fine at small scale, won't hold up as event history grows.

## Running Locally

\```bash
Requires Java 21+, Maven, PostgreSQL running locally
createdb sync_engine_db
mvn spring-boot:run
\```

## Author

Balaji — transitioning into backend development, built to demonstrate
distributed systems concepts in a practical, Java-based project.