package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.SyncPullRequest;

import com.balaji.sync_engine.dto.SyncPullResponse;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.service.SyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.balaji.sync_engine.security.User;
import com.balaji.sync_engine.security.Role;
import com.balaji.sync_engine.security.UserRepository;

@Tag(name = "Sync", description = "Delta sync push/pull for offline-first devices")
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final UserRepository userRepository;

    public SyncController(SyncService syncService, UserRepository userRepository) {
        this.syncService = syncService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Pull changes since a checkpoint",
            description = "Pass lastSyncedHlc as null for a first-time sync. Responses are "
                    + "capped at `limit` (default 500, max 1000) events; when hasMore is true, "
                    + "call pull again immediately using the returned newCheckpointHlc to "
                    + "continue -- do not treat a capped response as 'fully synced'.")
	 @PostMapping("/pull")
	 @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
	 public ResponseEntity<SyncPullResponse> pull(@RequestBody SyncPullRequest request) {
	    	return ResponseEntity.ok(syncService.pull(request));
	 }

    @Operation(summary = "Push a batch of offline changes",
            description = "Each event is processed independently -- one malformed event "
                    + "does not roll back the rest of the batch. FIELD_WORKER accounts "
                    + "must push under their own bound deviceId; a mismatch returns 403. "
                    + "Max 200 events per request.")
	 @ApiResponse(responseCode = "200", description = "Processed (see response body for "
	         + "per-event acceptance, conflicts, and rejections)")
	 @ApiResponse(responseCode = "400", description = "Batch exceeds the 200-event limit")
	 @ApiResponse(responseCode = "403", description = "Claimed deviceId does not match the "
	         + "authenticated account's bound device")
	 @PostMapping("/push")
	 @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<SyncPushResponse> push(@RequestBody SyncPushRequest request,
                                                  Authentication authentication) {
        String authenticatedUsername = authentication.getName();
        User user = userRepository.findByUsername(authenticatedUsername).orElseThrow();

        if (user.getRole() == Role.FIELD_WORKER && !request.deviceId().equals(user.getDeviceId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated device identity does not match claimed deviceId in request");
        }

        return ResponseEntity.ok(syncService.push(request));
    }
}