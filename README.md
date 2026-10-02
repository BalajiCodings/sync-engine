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
- [ ] Phase 4 — Delta sync pull/push endpoints
- [ ] Phase 5 — Field-level conflict resolution engine
- [ ] Phase 6 — Idempotency & partial sync handling
- [ ] Phase 7 — Testing (JUnit, Mockito, Testcontainers)
- [ ] Phase 8 — Observability (Micrometer/Actuator)
- [ ] Phase 9 — CLI client simulator for multi-device demo

## Running Locally

\```bash
# Requires Java 21+, Maven, PostgreSQL running locally
createdb sync_engine_db
mvn spring-boot:run
\```

## Author

Balaji — transitioning into backend development, built to demonstrate
distributed systems concepts in a practical, Java-based project.