package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.entity.PatientRecord;

import com.balaji.sync_engine.service.PatientRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@Tag(name = "Patient Records", description = "Direct CRUD for patient records (REST path)")
@RestController
@RequestMapping("/api/patients")
public class PatientRecordController {

    private final PatientRecordService service;

    public PatientRecordController(PatientRecordService service) {
        this.service = service;
    }

    @Operation(summary = "Create a patient record")
    @PostMapping
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<PatientRecord> create(@RequestBody PatientRecord record) {
        PatientRecord saved = service.create(record);
        return ResponseEntity.status(201).body(saved);
    }

    @Operation(summary = "List all non-deleted patient records")
    @GetMapping
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<List<PatientRecord>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @Operation(summary = "Get a single patient by ID",
            description = "Returns the record even if soft-deleted, unlike findAll().")
 @GetMapping("/{id}")
 @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<PatientRecord> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @Operation(summary = "Update a patient record")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<PatientRecord> update(@PathVariable UUID id, @RequestBody PatientRecord record) {
        return ResponseEntity.ok(service.update(id, record));
    }

    @Operation(summary = "Soft-delete a patient record",
            description = "Restricted to SUPERVISOR/ADMIN -- field workers cannot delete directly.")
	 @ApiResponse(responseCode = "403", description = "Field workers are not permitted to delete")
	 @DeleteMapping("/{id}")
	 @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}