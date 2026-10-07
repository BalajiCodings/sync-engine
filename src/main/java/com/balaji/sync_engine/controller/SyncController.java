package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.SyncPullRequest;

import com.balaji.sync_engine.dto.SyncPullResponse;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.service.SyncService;
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

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final UserRepository userRepository;

    public SyncController(SyncService syncService, UserRepository userRepository) {
        this.syncService = syncService;
        this.userRepository = userRepository;
    }

    @PostMapping("/pull")
    public ResponseEntity<SyncPullResponse> pull(@RequestBody SyncPullRequest request) {
        return ResponseEntity.ok(syncService.pull(request));
    }

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