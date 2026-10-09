package com.balaji.sync_engine.controller;


import com.balaji.sync_engine.conflict.ConflictStatus;
import com.balaji.sync_engine.dto.ApiResult;
import com.balaji.sync_engine.dto.ConflictView;
import com.balaji.sync_engine.dto.PageResponse;
import com.balaji.sync_engine.dto.ResolveConflictRequest;
import com.balaji.sync_engine.entity.ConflictRecord;
import com.balaji.sync_engine.service.ConflictReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
@Tag(name = "Conflicts", description = "Review and resolve conflicts flagged during sync (SUPERVISOR/ADMIN only)")
@RestController
@RequestMapping("/api/conflicts")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public class ConflictController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ConflictReviewService conflictReviewService;

    public ConflictController(ConflictReviewService conflictReviewService) {
        this.conflictReviewService = conflictReviewService;
    }

    @Operation(summary = "List conflicts (paginated, oldest first)",
               description = "Filter with ?status=PENDING or ?status=RESOLVED; omit for all. "
                       + "Oldest-first because supervisors work this as a queue.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<ConflictView>>> list(
            @RequestParam(required = false) ConflictStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("detectedAt").ascending());
        Page<ConflictView> result = conflictReviewService.list(status, pageable).map(ConflictView::from);
        return ResponseEntity.ok(ApiResult.ok(PageResponse.from(result)));
    }

    @Operation(summary = "Get one conflict by ID")
    @ApiResponse(responseCode = "404", description = "No conflict with that ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<ConflictView>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.ok(ConflictView.from(conflictReviewService.get(id))));
    }

    @Operation(summary = "Resolve a pending conflict",
               description = "FIELD_CONFLICT accepts KEEP_CURRENT, ACCEPT_INCOMING or CUSTOM_VALUE. "
                       + "DELETE_UPDATE_CONFLICT accepts CONFIRM_DELETE or RESTORE_ENTITY. The decision is "
                       + "appended to the event log as a server-originated change so every device receives "
                       + "it on its next pull. resolvedBy is taken from the authenticated user.")
    @ApiResponse(responseCode = "400", description = "Action invalid for this conflict type, or customValue missing")
    @ApiResponse(responseCode = "404", description = "No conflict with that ID")
    @ApiResponse(responseCode = "409", description = "Already resolved, or resolved concurrently by someone else")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResult<ConflictView>> resolve(@PathVariable UUID id,
                                                           @RequestBody ResolveConflictRequest request,
                                                           Authentication authentication) {
        ConflictRecord resolved = conflictReviewService.resolve(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResult.ok(ConflictView.from(resolved)));
    }
}