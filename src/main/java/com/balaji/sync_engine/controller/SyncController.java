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

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/pull")
    public ResponseEntity<SyncPullResponse> pull(@RequestBody SyncPullRequest request) {
        return ResponseEntity.ok(syncService.pull(request));
    }

    @PostMapping("/push")
    public ResponseEntity<SyncPushResponse> push(@RequestBody SyncPushRequest request) {
        return ResponseEntity.ok(syncService.push(request));
    }
}