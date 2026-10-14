package com.balaji.sync_engine.simulator;

import com.balaji.sync_engine.clock.HLCTimestamp;
import com.balaji.sync_engine.clock.HybridLogicalClock;
import com.balaji.sync_engine.crdt.PnCounter;
import com.balaji.sync_engine.crdt.PnCounterCodec;
import com.balaji.sync_engine.dto.SyncPullRequest;
import com.balaji.sync_engine.dto.SyncPullResponse;
import com.balaji.sync_engine.dto.SyncPushRequest;
import com.balaji.sync_engine.dto.SyncPushResponse;
import com.balaji.sync_engine.entity.ChangeEvent;
import com.balaji.sync_engine.entity.ChangeType;
import com.balaji.sync_engine.service.SyncService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * Emulates a real offline-first field device (e.g., tablet running local SQLite).
 * Maintains an independent clock, local entity cache, and offline mutation buffer.
 */
public class SimulatedDevice {

    private final String deviceId;
    private final HybridLogicalClock clock;
    private final PnCounterCodec counterCodec;
    private final JsonMapper jsonMapper = new JsonMapper();

    // Local in-memory replica state: entityId -> (fieldName -> fieldValue)
    private final Map<UUID, Map<String, String>> localFields = new HashMap<>();
    // Provenance tracking: entityId -> (fieldName -> lastWriteHlc)
    private final Map<UUID, Map<String, String>> localFieldHlc = new HashMap<>();

    // Outbound offline queue waiting to be pushed
    private final List<ChangeEvent> outboundQueue = new ArrayList<>();

    // The highest server_seq this device has received
    private long lastSyncedSeq = 0L;

    public SimulatedDevice(String deviceId, PnCounterCodec counterCodec) {
        this.deviceId = deviceId;
        this.clock = new HybridLogicalClock(deviceId);
        this.counterCodec = counterCodec;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public long getLastSyncedSeq() {
        return lastSyncedSeq;
    }

    /**
     * Simulates an offline user mutation on the device.
     */
    public void mutateOffline(UUID entityId, String fieldName, String value) {
        HLCTimestamp ts = clock.tick();

        // 1. Update local cache
        localFields.computeIfAbsent(entityId, k -> new HashMap<>()).put(fieldName, value);
        localFieldHlc.computeIfAbsent(entityId, k -> new HashMap<>()).put(fieldName, ts.toString());

        // 2. Buffer into outbound sync queue
        ChangeEvent event = new ChangeEvent();
        event.setEventId(UUID.randomUUID());
        event.setEntityType("PatientRecord");
        event.setEntityId(entityId);
        event.setChangeType(ChangeType.UPDATE);
        event.setDeviceId(deviceId);
        event.setHlcTimestamp(ts.toString());
        event.setBasedOnSeq(lastSyncedSeq);
        event.setPayload("{\"" + fieldName + "\":\"" + value + "\"}");

        outboundQueue.add(event);
    }

    /**
     * Simulates an offline increment/decrement to a PN-Counter CRDT field.
     */
    public void mutateCounterOffline(UUID entityId, String fieldName, long delta) {
        HLCTimestamp ts = clock.tick();

        // Build or merge local PN-Counter
        String existingJson = getField(entityId, fieldName);
        PnCounter existing = (existingJson == null) ? PnCounter.empty() : counterCodec.parse(existingJson);

        Map<String, Long> inc = new HashMap<>(existing.increments());
        Map<String, Long> dec = new HashMap<>(existing.decrements());

        if (delta >= 0) {
            inc.put(deviceId, inc.getOrDefault(deviceId, 0L) + delta);
        } else {
            dec.put(deviceId, dec.getOrDefault(deviceId, 0L) + Math.abs(delta));
        }

        PnCounter updated = new PnCounter(inc, dec);
        String updatedJson = counterCodec.write(updated);

        localFields.computeIfAbsent(entityId, k -> new HashMap<>()).put(fieldName, updatedJson);
        localFieldHlc.computeIfAbsent(entityId, k -> new HashMap<>()).put(fieldName, ts.toString());

        // Create outbound change event (only carrying this device's own delta)
        PnCounter deltaPayload = new PnCounter(
                delta >= 0 ? Map.of(deviceId, delta) : Map.of(),
                delta < 0 ? Map.of(deviceId, Math.abs(delta)) : Map.of()
        );

        ChangeEvent event = new ChangeEvent();
        event.setEventId(UUID.randomUUID());
        event.setEntityType("PatientRecord");
        event.setEntityId(entityId);
        event.setChangeType(ChangeType.UPDATE);
        event.setDeviceId(deviceId);
        event.setHlcTimestamp(ts.toString());
        event.setBasedOnSeq(lastSyncedSeq);
        event.setPayload("{\"" + fieldName + "\":" + counterCodec.write(deltaPayload) + "}");

        outboundQueue.add(event);
    }

    /**
     * Pushes all buffered offline mutations to the server.
     */
    public SyncPushResponse push(SyncService syncService) {
        if (outboundQueue.isEmpty()) {
            return new SyncPushResponse(0, List.of(), List.of(), clock.tick().toString());
        }
        List<ChangeEvent> batch = new ArrayList<>(outboundQueue);
        SyncPushResponse response = syncService.push(new SyncPushRequest(deviceId, batch));
        outboundQueue.clear();
        return response;
    }

    /**
     * Pulls delta changes from the server and applies them locally.
     */
    public void pull(SyncService syncService) {
        boolean hasMore = true;
        while (hasMore) {
            SyncPullResponse response = syncService.pull(new SyncPullRequest(deviceId, lastSyncedSeq, 100));
            clock.update(HLCTimestamp.parse(response.serverHlc()));

            for (ChangeEvent change : response.changes()) {
                applyRemoteChange(change);
            }

            lastSyncedSeq = response.nextCursor();
            hasMore = response.hasMore();
        }
    }

    private void applyRemoteChange(ChangeEvent change) {
        if (change.getChangeType() == ChangeType.DELETE) {
            localFields.remove(change.getEntityId());
            return;
        }

        JsonNode payload = jsonMapper.readTree(change.getPayload());
        UUID entityId = change.getEntityId();
        HLCTimestamp incomingTs = HLCTimestamp.parse(change.getHlcTimestamp());

        payload.properties().forEach(entry -> {
            String field = entry.getKey();
            JsonNode valueNode = entry.getValue();

            // 1. CRDT Counter Field: payload is a JSON object {"increments":..., "decrements":...}
            if ("dosesAdministered".equals(field)) {
                String jsonStr = valueNode.isTextual() ? valueNode.asString() : valueNode.toString();
                PnCounter incoming = counterCodec.parse(jsonStr);
                String cur = getField(entityId, field);
                PnCounter current = (cur == null) ? PnCounter.empty() : counterCodec.parse(cur);
                localFields.computeIfAbsent(entityId, k -> new HashMap<>())
                           .put(field, counterCodec.write(current.merge(incoming)));
                return;
            }

            // 2. Scalar Field: extract string safely without coercing objects
            String incomingVal = valueNode.isNull() ? null
                    : (valueNode.isObject() || valueNode.isArray()) ? valueNode.toString() : valueNode.asString();

            // 3. Scalar Last-Write-Wins Merge
            String currentHlcStr = localFieldHlc.computeIfAbsent(entityId, k -> new HashMap<>()).get(field);
            if (currentHlcStr == null || incomingTs.compareTo(HLCTimestamp.parse(currentHlcStr)) > 0) {
                localFields.computeIfAbsent(entityId, k -> new HashMap<>()).put(field, incomingVal);
                localFieldHlc.get(entityId).put(field, incomingTs.toString());
            }
        });
    }

    public String getField(UUID entityId, String fieldName) {
        Map<String, String> fields = localFields.get(entityId);
        return fields != null ? fields.get(fieldName) : null;
    }
}