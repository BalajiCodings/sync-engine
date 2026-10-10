package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.ApiResult;
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
import org.springframework.security.access.AccessDeniedException;
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

    @Operation(summary = "Pull changes since a cursor",
            description = "Send cursor=null on a device's first sync, then send back nextCursor from the "
                    + "previous response. Capped at `limit` (default 500, max 1000); when hasMore is true, "
                    + "pull again immediately. The cursor is a position in the server's arrival order, so "
                    + "events that reach the server late from offline devices are always delivered, whatever "
                    + "their timestamps. serverHlc is for clock synchronization only.")
    @PostMapping("/pull")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<SyncPullResponse>> pull(@RequestBody SyncPullRequest request) {
        return ResponseEntity.ok(ApiResult.ok(syncService.pull(request)));
    }

    @Operation(summary = "Push a batch of offline changes",
               description = "Each event is processed independently, so one bad event does not roll back the "
                       + "rest. success=true means the request was processed; check data.rejectedEvents and "
                       + "data.conflicts for per-event outcomes. FIELD_WORKER accounts must push under their own "
                       + "bound deviceId. Max 200 events per request.")
    @ApiResponse(responseCode = "400", description = "Batch exceeds the 200-event limit")
    @ApiResponse(responseCode = "403", description = "Claimed deviceId does not match the authenticated account")
    @PostMapping("/push")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<SyncPushResponse>> push(@RequestBody SyncPushRequest request,
		            Authentication authentication) {
		if (request.changes() == null) {
		throw new IllegalArgumentException("changes is required");
		}
		User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
		
		if (user.getRole() == Role.FIELD_WORKER) {
				String boundDevice = user.getDeviceId();
				boolean spoofed = !boundDevice.equals(request.deviceId())
				|| request.changes().stream().anyMatch(c -> !boundDevice.equals(c.getDeviceId()));
				if (spoofed) {
					throw new AccessDeniedException(
					"Every event must be pushed under the authenticated account's own deviceId");
			}
		}
		
		return ResponseEntity.ok(ApiResult.ok(syncService.push(request)));
		}
}