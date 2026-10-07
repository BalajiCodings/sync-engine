package com.balaji.sync_engine.dto;

import java.util.UUID;

public record RejectedEvent(UUID eventId, String reason) {}