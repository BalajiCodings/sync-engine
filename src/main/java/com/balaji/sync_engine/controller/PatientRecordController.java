package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.ApiResult;
import com.balaji.sync_engine.dto.PageResponse;
import com.balaji.sync_engine.entity.PatientRecord;


import com.balaji.sync_engine.service.PatientRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

@Tag(name = "Patient Records", description = "Direct CRUD for patient records (REST path)")
@RestController
@RequestMapping("/api/patients")
public class PatientRecordController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PatientRecordService service;

    public PatientRecordController(PatientRecordService service) {
        this.service = service;
    }

    @Operation(summary = "Create a patient record")
    @ApiResponse(responseCode = "201", description = "Patient created")
    @PostMapping
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PatientRecord>> create(@RequestBody PatientRecord record) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.create(record)));
    }

    @Operation(summary = "List non-deleted patient records (paginated)",
               description = "size is capped at 100. A negative page or size < 1 returns 400.")
    @GetMapping
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PageResponse<PatientRecord>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PatientRecord> result = service.findAll(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
        return ResponseEntity.ok(ApiResult.ok(PageResponse.from(result)));
    }

    @Operation(summary = "Get a single patient by ID",
               description = "Returns the record even if soft-deleted, unlike the list endpoint.")
    @ApiResponse(responseCode = "404", description = "No patient with that ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PatientRecord>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.ok(service.findById(id)));
    }

    @Operation(summary = "Update a patient record")
    @ApiResponse(responseCode = "404", description = "No patient with that ID")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PatientRecord>> update(@PathVariable UUID id, @RequestBody PatientRecord record) {
        return ResponseEntity.ok(ApiResult.ok(service.update(id, record)));
    }

    @Operation(summary = "Soft-delete a patient record",
               description = "Restricted to SUPERVISOR/ADMIN. Returns 200 with an empty envelope, not 204, "
                       + "so clients can always parse a JSON body.")
    @ApiResponse(responseCode = "403", description = "Field workers are not permitted to delete")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResult.empty());
    }
}