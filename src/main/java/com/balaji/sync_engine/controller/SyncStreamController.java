package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.ApiResult;
import com.balaji.sync_engine.dto.StreamTicketResponse;
import com.balaji.sync_engine.security.User;
import com.balaji.sync_engine.security.UserRepository;
import com.balaji.sync_engine.service.StreamTicketService;
import com.balaji.sync_engine.service.SyncStreamHub;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Sync Stream", description = "Live 'changes available' notifications (Server-Sent Events)")
@RestController
@RequestMapping({"/api/v1/sync", "/api/sync"})
public class SyncStreamController {

    private final StreamTicketService ticketService;
    private final SyncStreamHub hub;
    private final UserRepository userRepository;

    public SyncStreamController(StreamTicketService ticketService, SyncStreamHub hub,
                                UserRepository userRepository) {
        this.ticketService = ticketService;
        this.hub = hub;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Get a single-use ticket for opening the live stream",
               description = "Valid for 30 seconds and usable once. Needed because browser EventSource cannot "
                       + "send an Authorization header.")
    @PostMapping("/stream-ticket")
    @PreAuthorize("hasAnyRole('FIELD_WORKER', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ApiResult<StreamTicketResponse>> issueTicket(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        String ticket = ticketService.issue(user.getUsername(), user.getDeviceId());
        return ResponseEntity.ok(ApiResult.ok(
                new StreamTicketResponse(ticket, StreamTicketService.TTL.toSeconds())));
    }

    @Operation(summary = "Open the live notification stream (text/event-stream)",
               description = "A doorbell, not a data feed. Events: `ready` (sent on connect) and `changes`, "
                       + "both with data {\"headSeq\": n}, plus `: keepalive` comments every 25s. When headSeq "
                       + "is ahead of your cursor, call POST /api/sync/pull until hasMore is false. A device "
                       + "is not notified about changes only it pushed. The ticket is single-use, so on any "
                       + "disconnect request a new ticket and reconnect, and pull once after reconnecting. "
                       + "Swagger UI cannot display a live stream; use curl -N or EventSource.")
    @ApiResponse(responseCode = "200", description = "Event stream opened")
    @ApiResponse(responseCode = "401", description = "Ticket invalid, expired, or already used")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        StreamTicketService.Ticket redeemed = ticketService.redeem(ticket);
        return hub.open(redeemed.username(), redeemed.deviceId());
    }
}