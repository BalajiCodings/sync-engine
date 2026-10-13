package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.*;
import com.balaji.sync_engine.entity.PatientRecord;
import com.balaji.sync_engine.service.PatientRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Patient Records", description = "Direct CRUD for patient records (REST path)")
@RestController
@RequestMapping({"/api/v1/patients", "/api/patients"})
public class PatientRecordController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PatientRecordService service;

    public PatientRecordController(PatientRecordService service) {
        this.service = service;
    }

    @Operation(summary = "Create a patient record")
    @ApiResponse(responseCode = "201", description = "Patient created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @PostMapping
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PatientResponse>> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientRecord toCreate = new PatientRecord();
        toCreate.setId(request.id());
        toCreate.setPatientName(request.patientName());
        toCreate.setWeight(request.weight());
        toCreate.setBloodPressure(request.bloodPressure());
        toCreate.setDosage(request.dosage());

        PatientRecord created = service.create(toCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(PatientResponse.from(created)));
    }

    @Operation(summary = "List non-deleted patient records (paginated)",
               description = "size is capped at 100. A negative page or size < 1 returns 400.")
    @GetMapping
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PageResponse<PatientResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
        }
        Page<PatientResponse> result = service.findAll(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
                .map(PatientResponse::from);
        return ResponseEntity.ok(ApiResult.ok(PageResponse.from(result)));
    }

    @Operation(summary = "Get a single patient by ID",
               description = "Returns the record even if soft-deleted, unlike the list endpoint.")
    @ApiResponse(responseCode = "404", description = "No patient with that ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PatientResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.ok(PatientResponse.from(service.findById(id))));
    }

    @Operation(summary = "Update a patient record")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "No patient with that ID")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<PatientResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request) {
        PatientRecord toUpdate = new PatientRecord();
        toUpdate.setPatientName(request.patientName());
        toUpdate.setWeight(request.weight());
        toUpdate.setBloodPressure(request.bloodPressure());
        toUpdate.setDosage(request.dosage());

        PatientRecord updated = service.update(id, toUpdate);
        return ResponseEntity.ok(ApiResult.ok(PatientResponse.from(updated)));
    }

    @Operation(summary = "Soft-delete a patient record",
               description = "Restricted to SUPERVISOR/ADMIN. Returns 200 with an empty envelope.")
    @ApiResponse(responseCode = "403", description = "Field workers are not permitted to delete")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResult.empty());
    }
}